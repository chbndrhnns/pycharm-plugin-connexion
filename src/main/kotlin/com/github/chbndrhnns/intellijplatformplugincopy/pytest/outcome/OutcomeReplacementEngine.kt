package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.psi.*

class OutcomeReplacementEngine {

    fun apply(project: Project, assertStatement: PyAssertStatement, diff: OutcomeDiff, matchedKey: String) {
        val expected = diff.expected
        val actual = diff.actual

        val expression = assertStatement.arguments.firstOrNull()
        if (expression is PyBinaryExpression && expression.isOperator("==")) {
            val right = expression.rightExpression

            // Convention: the expected value is on the right side of the assert.
            // "Use actual outcome" should update the right side (or the value it references).
            val expectedExpression = right ?: return
            if (expectedExpression is PyReferenceExpression) {
                when (val resolved = expectedExpression.reference.resolve()) {
                    is PyTargetExpression -> {
                        val assignedValue = resolved.findAssignedValue()
                        if (assignedValue != null) {
                            replaceElement(assignedValue, actual, expected, project, matchedKey)
                            return
                        }
                    }

                    is PyParameter -> {
                        replaceElement(expectedExpression, actual, expected, project, matchedKey)
                        return
                    }
                }
            }

            replaceElement(expectedExpression, actual, expected, project, matchedKey)
            return
        }

        var replaced = false
        assertStatement.accept(object : PyRecursiveElementVisitor() {
            override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
                if (replaced) return
                if (matchesLiteral(node.stringValue, expected) || matchesLiteral(node.text, expected)) {
                    replaceElement(node, actual, expected, project, matchedKey)
                    replaced = true
                }
                super.visitPyStringLiteralExpression(node)
            }

            override fun visitPyNumericLiteralExpression(node: PyNumericLiteralExpression) {
                if (replaced) return
                if (matchesLiteral(node.text, expected)) {
                    replaceElement(node, actual, expected, project, matchedKey)
                    replaced = true
                }
                super.visitPyNumericLiteralExpression(node)
            }
        })
    }

    private fun matches(element: PyExpression?, diffValue: String): Boolean {
        if (element == null) return false

        if (element is PyStringLiteralExpression) {
            if (matchesLiteral(element.stringValue, diffValue)) return true
            if (matchesLiteral(element.text, diffValue)) return true
        }
        if (element is PyNumericLiteralExpression) {
            if (matchesLiteral(element.text, diffValue)) return true
        }
        if (element is PyBoolLiteralExpression) {
            if (matchesLiteral(element.text, diffValue)) return true
        }
        if (element is PySequenceExpression) {
            val text = element.text
            if (normalize(text) == normalize(diffValue)) return true
        }

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
        if (codeValue == diffValue) return true
        if (codeValue == "\"$diffValue\"" || codeValue == "'$diffValue'") return true

        if (codeValue.length >= 2 && diffValue.length >= 2) {
            if (normalize(codeValue) == normalize(diffValue)) return true
        }

        return false
    }

    private fun normalize(s: String): String = s.replace("\"", "'")

    private fun replaceElement(
        element: PyElement,
        newValue: String,
        expectedValue: String,
        project: Project,
        matchedKey: String
    ) {
        val generator = PyElementGenerator.getInstance(project)

        if (element is PyReferenceExpression) {
            val resolved = element.reference.resolve()
            if (resolved is PyParameter) {
                updateParametrizedValue(resolved, newValue, expectedValue, project, matchedKey)
                return
            }
        }

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

        fun createExpression(text: String): PyExpression? {
            val trimmed = text.trim()
            try {
                val listExpr = generator.createExpressionFromText(
                    LanguageLevel.getDefault(),
                    "[$trimmed]"
                ) as? PyListLiteralExpression
                return listExpr?.elements?.firstOrNull()
                    ?: generator.createExpressionFromText(LanguageLevel.getDefault(), trimmed)
            } catch (_: IncorrectOperationException) {
                return null
            }
        }

        val newExpression = if (isStringTarget) {
            if (newValue.startsWith("\"") || newValue.startsWith("'")) {
                createExpression(newValue)
            } else {
                val quote = getPreferredQuote(element.containingFile)
                val useDouble = quote == "\""

                var escaped = StringUtil.escapeStringCharacters(newValue)
                if (!useDouble) {
                    escaped = escaped.replace("\\\"", "\"").replace("'", "\\'")
                }

                val text = "$quote$escaped$quote"
                createExpression(text)
            }
        } else {
            createExpression(newValue)
        }

        if (newExpression != null) {
            element.replace(newExpression)
        }
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
        project: Project,
        matchedKey: String
    ) {
        val pyFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return
        val decoratorList = pyFunction.decoratorList ?: return
        val decorator = decoratorList.findDecorator("pytest.mark.parametrize") ?: return

        val (namesArg, valuesArg) = getParametrizeArguments(decorator) ?: return
        val idsArg = getParametrizeIdsArgument(decorator)

        val paramName = parameter.name ?: return
        val paramIndex = getParameterIndex(namesArg, paramName)
        if (paramIndex == -1) return

        val paramValues = extractParameterValuesFromKey(matchedKey)
        if (valuesArg !is PyListLiteralExpression) return

        if (paramValues != null) {
            for (element in valuesArg.elements) {
                if (!matchesParameterSet(element, paramValues, namesArg)) continue
                val valueExpr = getValueExpression(element, paramIndex) ?: continue
                replaceElement(valueExpr, newValue, "", project, matchedKey)
                return
            }

            val idFromKey = paramValues.singleOrNull()
            val ids = extractParametrizeIds(idsArg)
            if (idFromKey != null && ids != null) {
                val index = ids.indexOf(idFromKey)
                if (index in valuesArg.elements.indices) {
                    val element = valuesArg.elements[index]
                    val valueExpr = getValueExpression(element, paramIndex)
                    if (valueExpr != null) {
                        replaceElement(valueExpr, newValue, "", project, matchedKey)
                        return
                    }
                }
            }
        }

        if (valuesArg.elements.size == 1) {
            val onlyElement = valuesArg.elements.firstOrNull()
            val valueExpr = onlyElement?.let { getValueExpression(it, paramIndex) }
            if (valueExpr != null) {
                replaceElement(valueExpr, newValue, "", project, matchedKey)
                return
            }
        }

        for (element in valuesArg.elements) {
            val valueExpr = getValueExpression(element, paramIndex) ?: continue
            if (matches(valueExpr, expectedValue)) {
                replaceElement(valueExpr, newValue, "", project, matchedKey)
                return
            }
        }
    }

    private fun extractParameterValuesFromKey(key: String): List<String>? {
        val bracketStart = key.lastIndexOf('[')
        val bracketEnd = key.lastIndexOf(']')
        if (bracketStart == -1 || bracketEnd == -1 || bracketStart >= bracketEnd) {
            return null
        }
        val paramPart = key.substring(bracketStart + 1, bracketEnd)
        return paramPart.split('-')
    }

    private fun matchesParameterSet(element: PyExpression, paramValues: List<String>, namesArg: PyExpression): Boolean {
        var current = element
        while (current is PyParenthesizedExpression) {
            val contained = current.containedExpression ?: return false
            current = contained
        }

        val elements = when (current) {
            is PyTupleExpression -> current.elements.toList()
            is PyListLiteralExpression -> current.elements.toList()
            else -> listOf(current)
        }

        if (elements.size != paramValues.size) return false

        for (i in elements.indices) {
            val elementValue = getElementValueAsString(elements[i])
            val expectedValue = paramValues[i]
            if (elementValue != expectedValue) {
                return false
            }
        }

        return true
    }

    private fun getElementValueAsString(element: PyExpression): String? {
        return when (element) {
            is PyStringLiteralExpression -> element.stringValue
            is PyNumericLiteralExpression -> element.text
            is PyBoolLiteralExpression -> element.text
            else -> element.text
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
        if (index == 0) return current
        return null
    }

    private fun getParametrizeArguments(decorator: PyDecorator): Pair<PyExpression, PyExpression>? {
        val argList = decorator.argumentList ?: return null

        var argNames = decorator.getKeywordArgument("argnames")
        var argValues = decorator.getKeywordArgument("argvalues")

        val args = argList.arguments

        if (argNames == null) {
            if (args.isNotEmpty() && args[0] !is PyKeywordArgument) {
                argNames = args[0]
            }
        }
        if (argValues == null) {
            if (args.size >= 2 && args[1] !is PyKeywordArgument) {
                argValues = args[1]
            }
        }

        if (argNames != null && argValues != null) {
            return Pair(argNames, argValues)
        }
        return null
    }

    private fun getParametrizeIdsArgument(decorator: PyDecorator): PyExpression? {
        val argList = decorator.argumentList ?: return null

        val keywordIds = decorator.getKeywordArgument("ids")
        if (keywordIds != null) return keywordIds

        val args = argList.arguments
        var positionalIndex = 0
        for (arg in args) {
            if (arg is PyKeywordArgument) continue
            if (positionalIndex == 3) return arg
            positionalIndex++
        }

        return null
    }

    private fun extractParametrizeIds(idsArg: PyExpression?): List<String>? {
        val expr = idsArg ?: return null
        val elements = when (expr) {
            is PyListLiteralExpression -> expr.elements.toList()
            is PyTupleExpression -> expr.elements.toList()
            else -> return null
        }
        return elements.mapNotNull {
            when (it) {
                is PyStringLiteralExpression -> it.stringValue
                else -> null
            }
        }
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
                if (text.contains("\"\"\"") || text.contains("'''")) return

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
}
