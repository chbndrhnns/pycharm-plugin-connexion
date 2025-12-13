package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.isDataclassClass
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyBuiltinNames
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.isPositionalOnlyCallable
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyReferenceExpression
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
            .filter { it.name?.startsWith("_") != true }
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
        // For dataclass constructors, recursive mode is useful when the dataclass contains either:
        //  - nested dataclass fields, or
        //  - alias-like fields (e.g. typing.NewType / TypeAlias / pydantic alias metadata)
        // even if the *missing parameter types* are not themselves dataclasses.
        val calleeClass = (call.callee as? PyReferenceExpression)
            ?.reference
            ?.resolve() as? PyClass

        // Pydantic-style models (and other dataclass-like frameworks) advertise constructor/field behavior
        // via `@dataclass_transform`. In those cases, recursive mode is meaningful even though we are not
        // dealing with a real `@dataclass`.
        if (calleeClass != null && isDataclassTransformClass(calleeClass, context)) return true

        if (calleeClass != null && isDataclassClass(calleeClass)) {
            val fields = PyDataclassFieldExtractor().extractDataclassFields(calleeClass, context)
            if (fields.any { field ->
                    val t = field.type
                    hasDataclassType(t, context) || field.aliasElement != null || isAliasLikeNonBuiltin(t)
                }
            ) return true
        }

        val missing = getMissingParameters(call, context)
        return missing.any { param ->
            val type = param.getType(context)
            hasDataclassType(type, context)
        }
    }

    private fun isAliasLikeNonBuiltin(type: PyType?): Boolean {
        return when (type) {
            is PyCollectionType -> type.elementTypes.any { isAliasLikeNonBuiltin(it) }

            is PyClassLikeType -> {
                val n = type.name ?: type.classQName?.substringAfterLast('.')
                !n.isNullOrBlank() && !PyBuiltinNames.isBuiltin(n)
            }

            is PyClassType -> {
                val n = type.name ?: type.classQName?.substringAfterLast('.')
                // Treat as alias-like when there is no concrete class behind it.
                val isAlias = type.pyClass == null || (n != null && type.pyClass?.name != n)
                isAlias && !n.isNullOrBlank() && !PyBuiltinNames.isBuiltin(n)
            }

            is PyUnionType -> type.members.any { isAliasLikeNonBuiltin(it) }
            else -> false
        }
    }

    private fun isDataclassTransformClass(pyClass: PyClass, context: TypeEvalContext): Boolean {
        val visited = HashSet<PyClass>()

        fun hasDataclassTransformDecorator(c: PyClass): Boolean {
            val decorators: Array<PyDecorator> = c.decoratorList?.decorators ?: return false
            return decorators.any { dec ->
                val text = dec.text
                // `@dataclass_transform(...)` or `@typing.dataclass_transform(...)`
                text.contains("dataclass_transform")
            }
        }

        fun dfs(c: PyClass): Boolean {
            if (!visited.add(c)) return false
            if (hasDataclassTransformDecorator(c)) return true

            // Walk base classes via PSI; sufficient for our test scenarios.
            for (baseExpr in c.superClassExpressions) {
                val resolved = (baseExpr as? PyReferenceExpression)?.reference?.resolve() as? PyClass
                if (resolved != null && dfs(resolved)) return true
            }
            return false
        }

        return dfs(pyClass)
    }

    private fun hasDataclassType(type: PyType?, context: TypeEvalContext): Boolean {
        return when (type) {
            is PyClassType -> isDataclassClass(type.pyClass)
            is PyUnionType -> type.members.any { hasDataclassType(it, context) }
            else -> false
        }
    }
}
