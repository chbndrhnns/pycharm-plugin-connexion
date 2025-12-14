package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.services.DiffData
import com.github.chbndrhnns.intellijplatformplugincopy.services.TestFailureState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class ReplaceExpectedWithActualIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = "Use actual test outcome"
    override fun getFamilyName(): String = "Use actual test outcome"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false

        // Only available on assert statements
        val assertStatement = PsiTreeUtil.getParentOfType(element, PyAssertStatement::class.java) ?: return false

        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java) ?: return false

        // Check if it looks like a test
        val name = pyFunction.name ?: return false
        if (!name.startsWith("test_")) return false

        val locationUrl = calculateLocationUrl(pyFunction) ?: return false
        val diffData = findDiffData(project, locationUrl)

        return diffData != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        val element = file.findElementAt(editor.caretModel.offset) ?: return

        val assertStatement = PsiTreeUtil.getParentOfType(element, PyAssertStatement::class.java) ?: return
        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java) ?: return

        val locationUrl = calculateLocationUrl(pyFunction) ?: return
        val diffData = findDiffData(project, locationUrl) ?: return

        val expected = diffData.expected
        val actual = diffData.actual

        // Strategy 1: Check if the assertion is a binary expression (e.g. assert A == B)
        // and if one of the operands matches the 'expected' value.
        val expression = assertStatement.arguments.firstOrNull()
        if (expression is PyBinaryExpression && expression.isOperator("==")) {
            val left = expression.leftExpression
            val right = expression.rightExpression

            if (matches(left, expected)) {
                replaceElement(left ?: return, actual, expected, project)
                return
            }
            if (matches(right, expected)) {
                if (right is PyReferenceExpression) {
                    val resolved = right.reference.resolve()
                    if (resolved is PyTargetExpression) {
                        val assignedValue = resolved.findAssignedValue()
                        if (assignedValue != null) {
                            replaceElement(assignedValue, actual, expected, project)
                            return
                        }
                    }
                }
                replaceElement(right ?: return, actual, expected, project)
                return
            }
        }

        // Strategy 2: Fallback to scanning the assertion for literals matching 'expected'
        // This covers cases where the match is inside a function call or list.
        val replaced = false
        assertStatement.accept(object : PyRecursiveElementVisitor() {
            override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
                if (replaced) return
                if (matchesLiteral(node.stringValue, expected) || matchesLiteral(node.text, expected)) {
                    replaceElement(node, actual, expected, project)
                }
                super.visitPyStringLiteralExpression(node)
            }

            override fun visitPyNumericLiteralExpression(node: PyNumericLiteralExpression) {
                if (replaced) return
                if (matchesLiteral(node.text, expected)) {
                    replaceElement(node, actual, expected, project)
                }
                super.visitPyNumericLiteralExpression(node)
            }
        })
    }

    private fun findDiffData(project: Project, baseLocationUrl: String): DiffData? {
        val state = TestFailureState.getInstance(project)
        val exact = state.getDiffData(baseLocationUrl)
        if (exact != null) return exact

        // Fuzzy match for parametrized tests
        // Keys usually look like: python<...>//...test_func[param]
        // We check if any key starts with the base URL.
        // Base URL: python<...>//...test_func
        return state.getAllKeys().asSequence()
            .filter { it.startsWith(baseLocationUrl) }.firstNotNullOfOrNull { state.getDiffData(it) }
    }

    private fun matches(element: PyExpression?, diffValue: String): Boolean {
        if (element == null) return false

        // 1. Literal match (String, Numeric)
        if (element is PyStringLiteralExpression) {
             if (matchesLiteral(element.stringValue, diffValue)) return true
             if (matchesLiteral(element.text, diffValue)) return true
        }
        if (element is PyNumericLiteralExpression) {
            if (matchesLiteral(element.text, diffValue)) return true
        }

        // 1.5 Boolean literal match (True, False)
        if (element is PyBoolLiteralExpression) {
            if (matchesLiteral(element.text, diffValue)) return true
        }

        // 2. Collection literal match (List, Dict, Tuple, Set)
        if (element is PySequenceExpression) {
            val text = element.text
            if (normalize(text) == normalize(diffValue)) return true
        }
        
        // 3. Reference match
        if (element is PyReferenceExpression) {
             val resolved = element.reference.resolve()
             if (resolved is PyTargetExpression) {
                 val assignedValue = resolved.findAssignedValue()
                 if (assignedValue != null && matches(assignedValue, diffValue)) return true
             }
            if (resolved is PyParameter) {
                if (isParametrizedMatch(resolved, diffValue)) return true
             }
        }
        
        return false
    }

    private fun matchesLiteral(codeValue: String, diffValue: String): Boolean {
        // Simple exact match first
        if (codeValue == diffValue) return true

        // Handle potential quote differences if diffValue is raw string
        // If code is "foo" and diff is foo
        if (codeValue == "\"$diffValue\"" || codeValue == "'$diffValue'") return true
        
        // Handle case where code has quotes and diff has different quotes
        // e.g. code="foo", diff='foo'. Use normalization.
        if (codeValue.length >= 2 && diffValue.length >= 2) {
             if (normalize(codeValue) == normalize(diffValue)) return true
        }

        return false
    }

    private fun normalize(s: String): String {
        // Replace double quotes with single quotes to standardize representation
        return s.replace("\"", "'")
    }

    private fun replaceElement(element: PyElement, newValue: String, expectedValue: String, project: Project) {
        val generator = PyElementGenerator.getInstance(project)

        // Handle parameter update
        if (element is PyReferenceExpression) {
            val resolved = element.reference.resolve()
            if (resolved is PyParameter) {
                updateParametrizedValue(resolved, newValue, expectedValue, project)
                return
            }
        }

        // Determine if we should treat newValue as a string literal content
        val isStringTarget = if (element is PyStringLiteralExpression) {
            true
        } else if (element is PyReferenceExpression) {
            val resolved = element.reference.resolve()
            if (resolved is PyTargetExpression) {
                val assignedValue = resolved.findAssignedValue()
                assignedValue is PyStringLiteralExpression
            } else {
                false
            }
        } else {
            false
        }

        // Helper to create expression safely avoiding DocString interpretation for strings
        fun createExpression(text: String): PyExpression {
            val listExpr = generator.createExpressionFromText(
                LanguageLevel.getDefault(),
                "[$text]"
            ) as? PyListLiteralExpression
            return listExpr?.elements?.firstOrNull()
                ?: generator.createExpressionFromText(LanguageLevel.getDefault(), text)
        }

        val newExpression = if (isStringTarget) {
            // If newValue is not quoted, quote it.
            // Be careful if newValue already has quotes.
            if (newValue.startsWith("\"") || newValue.startsWith("'")) {
                createExpression(newValue)
            } else {
                // It's a raw string value, quote it.
                // Detect preferred quote style from file content
                val quote = getPreferredQuote(element.containingFile)
                val useDouble = quote == "\""

                var escaped = StringUtil.escapeStringCharacters(newValue)
                if (!useDouble) {
                    // escapeStringCharacters assumes double quotes (escapes " as \")
                    // If we want single quotes:
                    // 1. Unescape double quotes: \" -> "
                    // 2. Escape single quotes: ' -> \'
                    escaped = escaped.replace("\\\"", "\"").replace("'", "\\'")
                }

                val text = "$quote$escaped$quote"
                createExpression(text)
            }
        } else {
            // For non-string literals (Dict, List, Ref), assume newValue is valid code.
            createExpression(newValue)
        }

        element.replace(newExpression)
    }

    private fun isParametrizedMatch(parameter: PyParameter, diffValue: String): Boolean {
        val pyFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return false
        val decoratorList = pyFunction.decoratorList ?: return false
        val decorator = decoratorList.findDecorator("pytest.mark.parametrize") ?: return false

        val (namesArg, valuesArg) = getParametrizeArguments(decorator) ?: return false

        val paramName = parameter.name ?: return false
        val paramIndex = getParameterIndex(namesArg, paramName)

        if (paramIndex == -1) return false

        if (valuesArg is PyListLiteralExpression) {
            for (element in valuesArg.elements) {
                val valueExpr = getValueExpression(element, paramIndex)
                if (valueExpr != null && matches(valueExpr, diffValue)) {
                    return true
                }
            }
        }
        return false
    }

    private fun updateParametrizedValue(
        parameter: PyParameter,
        newValue: String,
        expectedValue: String,
        project: Project
    ) {
        val pyFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return
        val decoratorList = pyFunction.decoratorList ?: return
        val decorator = decoratorList.findDecorator("pytest.mark.parametrize") ?: return

        val (namesArg, valuesArg) = getParametrizeArguments(decorator) ?: return

        val paramName = parameter.name ?: return
        val paramIndex = getParameterIndex(namesArg, paramName)

        if (paramIndex == -1) return

        if (valuesArg is PyListLiteralExpression) {
            for (element in valuesArg.elements) {
                val valueExpr = getValueExpression(element, paramIndex)
                if (valueExpr != null && matches(valueExpr, expectedValue)) {
                    // Update this value expression
                    // We need to call replaceElement recursively, but targetting the literal in the decorator
                    // We pass expectedValue as null or empty because we found the target already
                    replaceElement(valueExpr, newValue, "", project)
                    return
                }
            }
        }
    }

    private fun getValueExpression(element: PyExpression, index: Int): PyExpression? {
        var current = element
        while (current is PyParenthesizedExpression) {
            val contained = current.containedExpression ?: return null
            current = contained
        }

        if (current is PyTupleExpression) {
            return current.elements.getOrNull(index)
        }
        if (current is PyListLiteralExpression) {
            return current.elements.getOrNull(index)
        }
        // Single value (1 param case)
        if (index == 0) return current
        return null
    }

    private fun getParametrizeArguments(decorator: PyDecorator): Pair<PyExpression, PyExpression>? {
        val argList = decorator.argumentList ?: return null

        var argNames = decorator.getKeywordArgument("argnames")
        var argValues = decorator.getKeywordArgument("argvalues")

        val args = argList.arguments

        if (argNames == null) {
            // First positional
            if (args.isNotEmpty() && args[0] !is PyKeywordArgument) {
                argNames = args[0]
            }
        }
        if (argValues == null) {
            // Second positional
            if (args.size >= 2 && args[1] !is PyKeywordArgument) {
                argValues = args[1]
            }
        }

        if (argNames != null && argValues != null) {
            return Pair(argNames!!, argValues!!)
        }
        return null
    }

    private fun getParameterIndex(namesArg: PyExpression, paramName: String): Int {
        if (namesArg is PyStringLiteralExpression) {
            val names = namesArg.stringValue.split(",").map { it.trim() }
            return names.indexOf(paramName)
        }
        if (namesArg is PyListLiteralExpression || namesArg is PyTupleExpression) {
            val elements = (namesArg as? PySequenceExpression)?.elements ?: return -1
            return elements.indexOfFirst {
                it is PyStringLiteralExpression && it.stringValue == paramName
            }
        }
        return -1
    }

    private fun getPreferredQuote(file: PsiFile): String {
        var doubleCount = 0
        var singleCount = 0
        val limit = 50
        var count = 0

        file.accept(object : PyRecursiveElementVisitor() {
            override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
                if (count >= limit) return
                
                val text = node.text
                // Skip triple quoted strings (likely docstrings or blocks)
                if (text.contains("\"\"\"") || text.contains("'''")) return
                
                // Determine quote char
                val quoteChar = text.find { it == '"' || it == '\'' }
                if (quoteChar != null) {
                    if (quoteChar == '"') doubleCount++ else singleCount++
                    count++
                }
                super.visitPyStringLiteralExpression(node)
            }
        })

        if (singleCount > doubleCount) return "'"
        return "\""
    }

    private fun calculateLocationUrl(function: PyFunction): String? {
        val qName = function.qualifiedName ?: return null
        val virtualFile = function.containingFile.virtualFile ?: return null
        val project = function.project

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val root = fileIndex.getSourceRootForFile(virtualFile) ?: fileIndex.getContentRootForFile(virtualFile)

        if (root != null) {
            return "python<${root.path}>://$qName"
        }
        return "python:$qName"
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF
}
