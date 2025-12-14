package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

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
        val diffData = TestFailureState.getInstance(project).getDiffData(locationUrl)

        return diffData != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        val element = file.findElementAt(editor.caretModel.offset) ?: return

        val assertStatement = PsiTreeUtil.getParentOfType(element, PyAssertStatement::class.java) ?: return
        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java) ?: return

        val locationUrl = calculateLocationUrl(pyFunction) ?: return
        val diffData = TestFailureState.getInstance(project).getDiffData(locationUrl) ?: return

        val expected = diffData.expected
        val actual = diffData.actual

        // Strategy 1: Check if the assertion is a binary expression (e.g. assert A == B)
        // and if one of the operands matches the 'expected' value.
        val expression = assertStatement.arguments.firstOrNull()
        if (expression is PyBinaryExpression && expression.isOperator("==")) {
            val left = expression.leftExpression
            val right = expression.rightExpression

            if (matches(left, expected)) {
                replaceElement(left ?: return, actual, project)
                return
            }
            if (matches(right, expected)) {
                if (right is PyReferenceExpression) {
                    val resolved = right.reference.resolve()
                    if (resolved is PyTargetExpression) {
                        val assignedValue = resolved.findAssignedValue()
                        if (assignedValue != null) {
                            replaceElement(assignedValue, actual, project)
                            return
                        }
                    }
                }
                replaceElement(right ?: return, actual, project)
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
                    replaceElement(node, actual, project)
                }
                super.visitPyStringLiteralExpression(node)
            }

            override fun visitPyNumericLiteralExpression(node: PyNumericLiteralExpression) {
                if (replaced) return
                if (matchesLiteral(node.text, expected)) {
                    replaceElement(node, actual, project)
                }
                super.visitPyNumericLiteralExpression(node)
            }
        })
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

    private fun replaceElement(element: PyElement, newValue: String, project: Project) {
        val generator = PyElementGenerator.getInstance(project)

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
