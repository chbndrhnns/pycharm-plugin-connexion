package com.github.chbndrhnns.betterpy.features.search

import com.github.chbndrhnns.betterpy.core.index.PyLambdaFileIndex
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex
import com.jetbrains.python.psi.types.*

/**
 * Handles matching of callable types (functions, lambdas) to callable-only protocols.
 * A callable-only protocol is one that only requires a `__call__` method.
 */
object PyCallableProtocolMatcher {

    /**
     * Checks if a protocol only requires a `__call__` method (i.e., it's a "callable protocol").
     * Such protocols can be satisfied by lambda functions or regular functions.
     */
    fun isCallableOnlyProtocol(protocol: PyClass, context: TypeEvalContext): Boolean {
        if (!PyProtocolImplementationsSearch.isProtocol(protocol, context)) {
            return false
        }

        val requiredMembers = getProtocolRequiredMembers(protocol)
        return requiredMembers.size == 1 && requiredMembers.first() == "__call__"
    }

    /**
     * Gets the `__call__` method from a protocol, if it exists.
     */
    fun getCallMethod(protocol: PyClass): PyFunction? {
        return protocol.findMethodByName("__call__", false, null)
    }

    /**
     * Checks if a callable type (lambda, function) is compatible with a protocol's `__call__` method.
     * 
     * A callable is compatible with a protocol's `__call__` if:
     * - The protocol has a `__call__` method
     * - The callable's return type is compatible with `__call__`'s return type (covariant)
     * - The callable's parameter types are compatible with `__call__`'s parameter types (contravariant)
     */
    fun isCallableCompatible(
        callableType: PyCallableType,
        protocol: PyClass,
        context: TypeEvalContext
    ): Boolean {
        // Get the __call__ method from the protocol
        val callMethod = getCallMethod(protocol) ?: return false

        // Check return type compatibility (covariant)
        val protocolReturnType = context.getReturnType(callMethod)
        val callableReturnType = callableType.getReturnType(context)

        if (protocolReturnType != null && callableReturnType != null) {
            if (!PyTypeChecker.match(protocolReturnType, callableReturnType, context)) {
                return false
            }
        }

        // Check parameter compatibility
        // Protocol's __call__ has 'self' as first param, callable doesn't
        val protocolParams = callMethod.parameterList.parameters.drop(1) // Skip 'self'
        val callableParams = callableType.getParameters(context) ?: return protocolParams.isEmpty()

        // Callable must have exactly the same number of parameters as protocol's __call__
        // (A lambda with more params than the protocol expects is not compatible)
        if (callableParams.size != protocolParams.size) {
            return false
        }

        // Check each parameter type
        for ((index, protocolParam) in protocolParams.withIndex()) {
            val callableParam = callableParams.getOrNull(index) ?: return false

            val protocolNamedParam = protocolParam as? PyNamedParameter
            if (protocolNamedParam != null) {
                val protocolParamType = context.getType(protocolNamedParam)
                val callableParamType = callableParam.getType(context)

                if (protocolParamType != null && callableParamType != null) {
                    // For contravariance: check compatibility
                    if (!PyTypeChecker.match(callableParamType, protocolParamType, context)) {
                        return false
                    }
                }
            }
        }

        return true
    }

    /**
     * Finds all top-level functions that match a callable-only protocol's __call__ signature.
     */
    fun findMatchingFunctions(
        protocol: PyClass,
        scope: GlobalSearchScope,
        context: TypeEvalContext
    ): Collection<PyFunction> {
        val matchingFunctions = mutableListOf<PyFunction>()
        val project = protocol.project
        val allFunctionNames = mutableListOf<String>()

        StubIndex.getInstance().processAllKeys(
            PyFunctionNameIndex.KEY, project
        ) { functionName ->
            allFunctionNames.add(functionName)
            true
        }

        for (functionName in allFunctionNames) {
            ProgressManager.checkCanceled()
            val functions = PyFunctionNameIndex.find(functionName, project, scope)
            for (func in functions) {
                // Only consider top-level functions (not methods)
                if (func.containingClass != null) continue
                // Exclude test functions
                if (PyTestDetection.isTestFunction(func)) continue

                val funcType = context.getType(func) as? PyCallableType ?: continue

                if (isCallableCompatible(funcType, protocol, context)) {
                    matchingFunctions.add(func)
                }
            }
        }

        return matchingFunctions
    }

    /**
     * Finds all lambda expressions that are used in a context where the protocol type is expected
     * and match the protocol's __call__ signature.
     * 
     * Only includes lambdas that are:
     * - Passed as arguments where the parameter type is the protocol
     * - Assigned to variables annotated with the protocol type
     * - Used in other contexts where the expected type is the protocol
     */
    fun findMatchingLambdas(
        protocol: PyClass,
        scope: GlobalSearchScope,
        context: TypeEvalContext
    ): Collection<PyLambdaExpression> {
        val matchingLambdas = mutableListOf<PyLambdaExpression>()
        val project = protocol.project
        val psiManager = PsiManager.getInstance(project)
        val protocolQName = protocol.qualifiedName

        // Use the lambda file index to find only files that contain lambdas
        val filesWithLambdas = PyLambdaFileIndex.findFilesWithLambdas(project, scope)

        for (virtualFile in filesWithLambdas) {
            ProgressManager.checkCanceled()
            val psiFile = psiManager.findFile(virtualFile) as? PyFile ?: continue

            // Find all lambda expressions in the file
            val lambdas = PsiTreeUtil.findChildrenOfType(psiFile, PyLambdaExpression::class.java)

            for (lambda in lambdas) {
                // Check if the lambda is used in a context where the protocol type is expected
                val expectedType = getExpectedTypeForLambda(lambda, context)
                if (expectedType == null) continue

                // Check if the expected type matches the protocol
                val expectedClass = (expectedType as? PyClassType)?.pyClass
                if (expectedClass != protocol && expectedClass?.qualifiedName != protocolQName) continue

                val lambdaType = context.getType(lambda) as? PyCallableType ?: continue

                if (isCallableCompatible(lambdaType, protocol, context)) {
                    matchingLambdas.add(lambda)
                }
            }
        }

        return matchingLambdas
    }

    /**
     * Gets the expected type for a lambda expression based on its usage context.
     * Returns null if no expected type can be determined.
     */
    private fun getExpectedTypeForLambda(lambda: PyLambdaExpression, context: TypeEvalContext): PyType? {
        val parent = lambda.parent

        // Case 1: Lambda is an argument in a function call
        if (parent is PyArgumentList) {
            val call = parent.parent as? PyCallExpression ?: return null
            val callee = call.callee ?: return null
            val calleeType = context.getType(callee)

            if (calleeType is PyCallableType) {
                val params = calleeType.getParameters(context) ?: return null
                val argIndex = parent.arguments.indexOf(lambda)
                if (argIndex >= 0 && argIndex < params.size) {
                    return params[argIndex].getType(context)
                }
            }
        }

        // Case 2: Lambda is assigned to a variable with type annotation
        if (parent is PyAssignmentStatement) {
            val targets = parent.targets
            if (targets.isNotEmpty()) {
                val target = targets[0]
                if (target is PyTargetExpression) {
                    val annotation = target.annotation?.value
                    if (annotation != null) {
                        return context.getType(annotation)
                    }
                }
            }
        }

        // Case 3: Lambda is the value in a named expression (walrus operator) or other contexts
        // For now, we only support the most common cases above

        return null
    }

    /**
     * Gets the required member names from a protocol.
     */
    private fun getProtocolRequiredMembers(protocol: PyClass): List<String> {
        val members = mutableListOf<String>()

        // Get methods
        for (method in protocol.methods) {
            val name = method.name ?: continue
            if (name.startsWith("_") && !name.startsWith("__")) continue
            if (PyProtocolTypeMatcher.shouldIgnoreMember(name)) continue
            members.add(name)
        }

        // Get class attributes
        for (attr in protocol.classAttributes) {
            val name = attr.name ?: continue
            if (name.startsWith("_")) continue
            members.add(name)
        }

        return members
    }
}
