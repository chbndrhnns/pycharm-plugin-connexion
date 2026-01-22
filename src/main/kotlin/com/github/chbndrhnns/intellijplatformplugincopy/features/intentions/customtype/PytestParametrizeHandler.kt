package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil

/**
 * Handles pytest.mark.parametrize-specific logic for the "Introduce custom type" intention.
 * 
 * This class encapsulates all pytest-related functionality to keep the core intention logic clean.
 */
class PytestParametrizeHandler {

    /**
     * Detects if a parameter belongs to a pytest.mark.parametrize test and infers its type
     * from the decorator values.
     * 
     * @return AnnotationTarget with inferred builtin type, or null if not a parametrize parameter
     */
    fun detectBareParametrizeParameter(
        parameter: PyNamedParameter,
        context: TypeEvalContext
    ): AnnotationTarget? {
        val paramName = parameter.name ?: return null

        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return null
        val decoratorList = function.decoratorList ?: return null

        for (decorator in decoratorList.decorators) {
            if (decorator.name != "parametrize") continue

            val args = decorator.argumentList?.arguments ?: continue
            if (args.size < 2) continue

            // Check if this parameter is in the parametrize list
            val namesArg = args[0]
            val paramNames = extractParameterNames(namesArg) ?: continue
            if (paramName !in paramNames) continue

            // Infer type from the values
            val valuesArg = args[1]
            val paramIndex = paramNames.indexOf(paramName)
            val builtinType = inferBuiltinTypeFromValues(valuesArg, paramIndex, paramNames.size, context) ?: continue

            // Create a synthetic annotation target
            return AnnotationTarget(
                builtinName = builtinType,
                annotationRef = null,
                ownerName = paramName,
                dataclassField = null,
            )
        }

        return null
    }

    /**
     * Wraps values in pytest.mark.parametrize decorator lists when introducing
     * a custom type for a test parameter.
     *
     * For example, when introducing a custom type for parameter "arg" in:
     *   @pytest.mark.parametrize("arg", [1, 2, 3])
     *   def test_(arg): ...
     *
     * This will wrap the list items to produce:
     *   @pytest.mark.parametrize("arg", [Arg(1), Arg(2), Arg(3)])
     * 
     * @return true if decorator values were wrapped, false otherwise
     */
    fun wrapParametrizeDecoratorValues(
        parameter: PyNamedParameter,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ): Boolean {
        val paramName = parameter.name ?: return false
        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return false
        val decoratorList = function.decoratorList ?: return false

        for (decorator in decoratorList.decorators) {
            if (decorator.name != "parametrize") continue

            val args = decorator.argumentList?.arguments ?: continue
            if (args.isEmpty()) continue

            // First argument should be the parameter name(s)
            val namesArg = args[0]
            val paramNames = extractParameterNames(namesArg) ?: continue

            // Check if our parameter is in the list
            if (paramName !in paramNames) continue

            // Second argument should be the values list
            if (args.size < 2) continue
            val valuesArg = args[1]

            // Find the index of our parameter in the names list
            val paramIndex = paramNames.indexOf(paramName)

            // Wrap the values
            when (valuesArg) {
                is PyListLiteralExpression -> {
                    wrapListItems(valuesArg, paramIndex, paramNames.size, wrapperTypeName, generator)
                    return true
                }

                is PyTupleExpression -> {
                    wrapTupleItems(valuesArg, paramIndex, paramNames.size, wrapperTypeName, generator)
                    return true
                }
            }
        }
        return false
    }

    private fun extractParameterNames(namesArg: PyExpression): List<String>? {
        return when (namesArg) {
            is PyStringLiteralExpression -> {
                namesArg.stringValue.split(',').map { it.trim() }
            }

            is PyListLiteralExpression -> {
                namesArg.elements.mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
            }

            is PyTupleExpression -> {
                namesArg.elements.mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
            }

            else -> null
        }
    }

    private fun inferBuiltinTypeFromValues(
        valuesArg: PyExpression,
        paramIndex: Int,
        paramCount: Int,
        context: TypeEvalContext
    ): String? {
        val values = when (valuesArg) {
            is PyListLiteralExpression -> valuesArg.elements
            is PyTupleExpression -> valuesArg.elements
            else -> return null
        }

        if (values.isEmpty()) return null

        // Get the first value to infer type
        val firstValue = if (paramCount == 1) {
            values.firstOrNull()
        } else {
            // Multiple parameters - each value should be a tuple/list
            val firstSet = values.firstOrNull() ?: return null
            when (firstSet) {
                is PyTupleExpression -> firstSet.elements.getOrNull(paramIndex)
                is PyListLiteralExpression -> firstSet.elements.getOrNull(paramIndex)
                else -> null
            }
        } ?: return null

        // Infer builtin type from the value
        val type = context.getType(firstValue)
        return when {
            type?.name == "int" -> "int"
            type?.name == "str" -> "str"
            type?.name == "float" -> "float"
            type?.name == "bool" -> "bool"
            else -> null
        }
    }

    private fun wrapListItems(
        listExpr: PyListLiteralExpression,
        paramIndex: Int,
        paramCount: Int,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        for (element in listExpr.elements) {
            val valueToWrap = extractValueAtIndex(element, paramIndex, paramCount) ?: continue
            wrapSingleValue(valueToWrap, wrapperTypeName, generator)
        }
    }

    private fun wrapTupleItems(
        tupleExpr: PyTupleExpression,
        paramIndex: Int,
        paramCount: Int,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        for (element in tupleExpr.elements) {
            val valueToWrap = extractValueAtIndex(element, paramIndex, paramCount) ?: continue
            wrapSingleValue(valueToWrap, wrapperTypeName, generator)
        }
    }

    private fun extractValueAtIndex(element: PyExpression, index: Int, totalParams: Int): PyExpression? {
        // If there's only one parameter, the element itself is the value
        if (totalParams == 1) {
            return element
        }

        // If there are multiple parameters, each element should be a tuple/list
        return when (element) {
            is PyTupleExpression -> element.elements.getOrNull(index)
            is PyListLiteralExpression -> element.elements.getOrNull(index)
            else -> null
        }
    }

    private fun wrapSingleValue(
        expr: PyExpression,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        // Don't wrap if already wrapped
        if (expr is PyCallExpression && expr.callee?.text == wrapperTypeName) {
            return
        }

        val wrapped = generator.createExpressionFromText(
            LanguageLevel.getLatest(),
            "$wrapperTypeName(${expr.text})"
        )
        PyReplaceExpressionUtil.replaceExpression(expr, wrapped)
    }
}
