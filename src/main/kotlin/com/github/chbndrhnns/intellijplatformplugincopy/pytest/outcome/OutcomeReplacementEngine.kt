package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.psi.*

class OutcomeReplacementEngine {

    fun apply(project: Project, assertStatement: PyAssertStatement, diff: OutcomeDiff, matchedKey: String) {
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
                            replaceElement(assignedValue, actual, project, matchedKey)
                            return
                        }
                    }

                    is PyParameter -> {
                        replaceElement(expectedExpression, actual, project, matchedKey)
                        return
                    }
                }
            }

            replaceElement(expectedExpression, actual, project, matchedKey)
            return
        }
    }

    private fun replaceElement(
        element: PyElement,
        newValue: String,
        project: Project,
        matchedKey: String
    ) {
        val generator = PyElementGenerator.getInstance(project)

        if (element is PyReferenceExpression) {
            val resolved = element.reference.resolve()
            if (resolved is PyParameter) {
                updateParametrizedValue(resolved, newValue, project, matchedKey)
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

    private fun updateParametrizedValue(
        parameter: PyParameter,
        newValue: String,
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

        if (valuesArg !is PyListLiteralExpression) return

        // Parametrized updates are only supported when we can uniquely identify the failing case
        // via a bracket id in the matched key AND an explicit `ids=[...]` argument.
        val paramIdFromKey = extractParamIdFromKey(matchedKey) ?: return

        if (idsArg == null) {
            notifyParametrizeIdsMustBeLiteral(project)
            return
        }

        val ids = extractParametrizeIds(idsArg)
        if (ids == null) {
            notifyParametrizeIdsMustBeLiteral(project)
            return
        }

        val index = ids.indexOf(paramIdFromKey)
        if (index !in valuesArg.elements.indices) return

        val element = valuesArg.elements[index]
        val valueExpr = getValueExpression(element, paramIndex) ?: return
        replaceElement(valueExpr, newValue, project, matchedKey)
    }

    private fun extractParamIdFromKey(key: String): String? {
        val bracketStart = key.lastIndexOf('[')
        val bracketEnd = key.lastIndexOf(']')
        if (bracketStart == -1 || bracketEnd == -1 || bracketStart >= bracketEnd) return null
        return key.substring(bracketStart + 1, bracketEnd)
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

        val ids = ArrayList<String>(elements.size)
        for (element in elements) {
            val id = when (element) {
                is PyStringLiteralExpression -> element.stringValue
                is PyNumericLiteralExpression -> element.text
                else -> return null
            }
            ids.add(id)
        }

        return ids
    }

    private fun notifyParametrizeIdsMustBeLiteral(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Python DDD Toolkit")
            .createNotification(
                "Use actual test outcome for parametrized tests only works with literal ids",
                "Provide literal values, e.g. ids=[\"case-1\", \"case-2\"]. Function-based ids are not supported.",
                NotificationType.INFORMATION
            )
            .notify(project)
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
