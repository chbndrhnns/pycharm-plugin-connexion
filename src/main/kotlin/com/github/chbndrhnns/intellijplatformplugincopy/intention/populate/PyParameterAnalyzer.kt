package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.isDataclassClass
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.isPositionalOnlyCallable
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*

/**
 * Analyzes call expressions to determine missing parameters.
 */
class PyParameterAnalyzer {

    /**
     * Returns all missing parameters for the given call expression.
     */
    fun getMissingParameters(call: PyCallExpression, context: TypeEvalContext): List<PyCallableParameter> {
        val resolveContext = PyResolveContext.defaultContext(context)
        val mappings = call.multiMapArguments(resolveContext)
        if (mappings.isEmpty()) return emptyList()

        val mapping = mappings.first()
        val callableType: PyCallableType = mapping.callableType ?: return emptyList()

        val allParams = callableType.getParameters(context) ?: return emptyList()
        val mapped = mapping.mappedParameters

        return allParams
            .asSequence()
            .filter { !it.isSelf }
            .filter { !it.isPositionalContainer && !it.isKeywordContainer }
            .filter { param -> !mapped.values.contains(param) }
            .filter { it.name != null }
            .filter { !it.name!!.startsWith("_") }
            .toList()
    }

    /**
     * Returns only missing required parameters (those without default values).
     */
    fun getMissingRequiredParameters(call: PyCallExpression, context: TypeEvalContext): List<PyCallableParameter> {
        return getMissingParameters(call, context).filter { !it.hasDefaultValue() }
    }

    /**
     * Checks if the intention should be available for the given call.
     */
    fun isAvailable(call: PyCallExpression, context: TypeEvalContext): Boolean {
        if (isPositionalOnlyCallable(call)) return false
        return getMissingParameters(call, context).isNotEmpty()
    }

    /**
     * Checks if recursive mode is applicable (i.e., any missing parameter has a dataclass type).
     */
    fun isRecursiveApplicable(call: PyCallExpression, context: TypeEvalContext): Boolean {
        val missing = getMissingParameters(call, context)
        return missing.any { param ->
            val type = param.getType(context)
            hasDataclassType(type, context)
        }
    }

    private fun hasDataclassType(type: PyType?, context: TypeEvalContext): Boolean {
        return when (type) {
            is PyClassType -> isDataclassClass(type.pyClass)
            is PyUnionType -> type.members.any { hasDataclassType(it, context) }
            else -> false
        }
    }
}
