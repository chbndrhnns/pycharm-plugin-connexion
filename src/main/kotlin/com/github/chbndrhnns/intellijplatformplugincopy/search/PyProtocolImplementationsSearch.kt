package com.github.chbndrhnns.intellijplatformplugincopy.search

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.codeInsight.typing.isProtocol as builtInIsProtocol

object PyProtocolImplementationsSearch {

    /**
     * Checks if a class is a Protocol.
     * Uses PyCharm's built-in check first, then falls back to name-based check
     * for test/mock environments where typing.Protocol may not be available.
     */
    fun isProtocol(pyClass: PyClass, context: TypeEvalContext): Boolean {
        val classType = context.getType(pyClass) as? PyClassType

        // Try PyCharm's built-in check first (works at runtime with real typing.Protocol)
        if (classType != null && builtInIsProtocol(classType, context)) {
            return true
        }

        // Fallback for test/mock environments where typing.Protocol is not available
        return pyClass.getAncestorClasses(context).any { it.name == "Protocol" }
    }

    fun search(
        protocol: PyClass, scope: GlobalSearchScope, context: TypeEvalContext
    ): Collection<PyClass> {
        val protocolType = context.getType(protocol) as? PyClassType ?: return emptyList()

        // Verify this is actually a Protocol
        if (!isProtocol(protocol, context)) {
            return emptyList()
        }

        val implementations = mutableListOf<PyClass>()
        val project = protocol.project

        // Get required member names from the Protocol
        val requiredMembers = getProtocolRequiredMembers(protocol)
        if (requiredMembers.isEmpty()) {
            return emptyList()
        }

        // Use the first required member to narrow down candidates
        val primaryMember = requiredMembers.first()
        val candidates = findCandidateClasses(project, scope, primaryMember)

        // Check each candidate for Protocol compatibility
        for (candidate in candidates) {
            ProgressManager.checkCanceled()

            if (candidate == protocol) continue
            if (candidate.isSubclass(protocol, context)) continue // Already an explicit inheritor
            if (isTestClass(candidate)) continue // Exclude test classes

            val candidateType = context.getType(candidate) as? PyClassType ?: continue

            if (matchesProtocol(protocolType, candidateType, context)) {
                implementations.add(candidate)
            }
        }

        return implementations
    }

    private fun getProtocolRequiredMembers(protocol: PyClass): List<String> {
        val members = mutableListOf<String>()

        // Get methods
        for (method in protocol.methods) {
            val name = method.name ?: continue
            if (name.startsWith("_") && !name.startsWith("__")) continue
            if (name in IGNORED_PROTOCOL_MEMBERS) continue
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

    private fun findCandidateClasses(
        project: Project, scope: GlobalSearchScope, memberName: String
    ): Collection<PyClass> {
        val allClasses = mutableSetOf<PyClass>()
        val allClassNames = mutableListOf<String>()

        StubIndex.getInstance().processAllKeys(
            PyClassNameIndex.KEY, project
        ) { className ->
            allClassNames.add(className)
            true
        }

        for (className in allClassNames) {
            ProgressManager.checkCanceled()
            val classes = PyClassNameIndex.find(className, project, scope)
            for (cls in classes) {
                // Quick check: does this class have the required member?
                if (cls.findMethodByName(memberName, false, null) != null || cls.findClassAttribute(
                        memberName,
                        false,
                        null
                    ) != null
                ) {
                    allClasses.add(cls)
                }
            }
        }

        return allClasses
    }

    private fun matchesProtocol(
        protocol: PyClassType, candidate: PyClassType, context: TypeEvalContext
    ): Boolean {
        // Manual structural subtyping check
        val protocolClass = protocol.pyClass
        val candidateClass = candidate.pyClass

        // 1. Check methods - must exist AND have compatible signatures
        for (protocolMethod in protocolClass.methods) {
            val name = protocolMethod.name ?: continue
            if (shouldIgnoreMember(name)) continue

            // Check if candidate has this method
            val candidateMethod = candidateClass.findMethodByName(name, true, context)
            if (candidateMethod != null) {
                // Method exists - check type compatibility
                if (!isMethodCompatible(protocolMethod, candidateMethod, context)) {
                    return false
                }
            } else {
                // Check if implemented as attribute (e.g. Callable)
                if (candidateClass.findClassAttribute(name, true, context) == null &&
                    candidateClass.findInstanceAttribute(name, true) == null
                ) {
                    return false
                }
            }
        }

        // 2. Check attributes - must exist AND have compatible types
        for (protocolAttr in protocolClass.classAttributes) {
            val name = protocolAttr.name ?: continue
            if (shouldIgnoreMember(name)) continue

            val candidateClassAttr = candidateClass.findClassAttribute(name, true, context)
            val candidateInstanceAttr = candidateClass.findInstanceAttribute(name, true)

            if (candidateClassAttr == null && candidateInstanceAttr == null) {
                return false
            }

            // Check type compatibility for attributes
            val protocolAttrType = context.getType(protocolAttr)
            if (protocolAttrType != null) {
                val candidateAttrType = when {
                    candidateClassAttr != null -> context.getType(candidateClassAttr)
                    candidateInstanceAttr != null -> context.getType(candidateInstanceAttr)
                    else -> null
                }

                // If protocol has a type annotation, candidate must have a compatible type
                if (candidateAttrType != null) {
                    if (!PyTypeChecker.match(protocolAttrType, candidateAttrType, context)) {
                        return false
                    }
                }
            }
        }

        return true
    }

    /**
     * Checks if a candidate method is compatible with a protocol method.
     * For protocol compatibility:
     * - Return types: candidate's return type must be a subtype of protocol's return type (covariant)
     * - Parameter types: protocol's parameter types must be subtypes of candidate's parameter types (contravariant)
     */
    private fun isMethodCompatible(
        protocolMethod: PyFunction,
        candidateMethod: PyFunction,
        context: TypeEvalContext
    ): Boolean {
        // Check return type compatibility (covariant)
        val protocolReturnType = context.getReturnType(protocolMethod)
        val candidateReturnType = context.getReturnType(candidateMethod)

        if (protocolReturnType != null && candidateReturnType != null) {
            // Candidate's return type must be assignable to protocol's return type
            if (!PyTypeChecker.match(protocolReturnType, candidateReturnType, context)) {
                return false
            }
        }

        // Check parameter compatibility
        val protocolParams = protocolMethod.parameterList.parameters
        val candidateParams = candidateMethod.parameterList.parameters

        // Skip 'self' parameter (first parameter in instance methods)
        val protocolNonSelfParams = protocolParams.drop(1)
        val candidateNonSelfParams = candidateParams.drop(1)

        // Candidate must have at least as many parameters as protocol
        // (can have more if they have defaults)
        if (candidateNonSelfParams.size < protocolNonSelfParams.size) {
            return false
        }

        // Check each parameter type (contravariant - protocol param type must be subtype of candidate param type)
        for ((index, protocolParam) in protocolNonSelfParams.withIndex()) {
            val candidateParam = candidateNonSelfParams.getOrNull(index) ?: return false

            // Cast to PyNamedParameter to access type information
            val protocolNamedParam = protocolParam as? PyNamedParameter
            val candidateNamedParam = candidateParam as? PyNamedParameter

            if (protocolNamedParam != null && candidateNamedParam != null) {
                val protocolParamType = context.getType(protocolNamedParam)
                val candidateParamType = context.getType(candidateNamedParam)

                if (protocolParamType != null && candidateParamType != null) {
                    // For contravariance: protocol's param type should be subtype of candidate's param type
                    // But for simplicity and practical matching, we check if they're compatible
                    if (!PyTypeChecker.match(candidateParamType, protocolParamType, context)) {
                        return false
                    }
                }
            }
        }

        return true
    }

    private fun shouldIgnoreMember(name: String): Boolean {
        return (name.startsWith("_") && !name.startsWith("__")) || name in IGNORED_PROTOCOL_MEMBERS
    }

    private val IGNORED_PROTOCOL_MEMBERS = setOf(
        "__init__", "__new__", "__slots__", "__class_getitem__", "__init_subclass__"
    )

    /**
     * Checks if a class is a test class (should be excluded from protocol implementations).
     * Test classes are identified by:
     * - Class name starting with "Test" (e.g., TestMyClass)
     * - Class name starting with "test_" (e.g., test_my_class - less common but possible)
     */
    fun isTestClass(pyClass: PyClass): Boolean {
        val name = pyClass.name ?: return false
        return name.startsWith("Test") || name.startsWith("test_")
    }

    /**
     * Checks if a function is a test function (should be excluded from protocol implementations).
     * Test functions are identified by:
     * - Function name starting with "test_" (e.g., test_my_function)
     */
    fun isTestFunction(pyFunction: PyFunction): Boolean {
        val name = pyFunction.name ?: return false
        return name.startsWith("test_")
    }

    /**
     * Checks if a protocol only requires a `__call__` method (i.e., it's a "callable protocol").
     * Such protocols can be satisfied by lambda functions or regular functions.
     */
    fun isCallableOnlyProtocol(protocol: PyClass, context: TypeEvalContext): Boolean {
        if (!isProtocol(protocol, context)) {
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
     * 
     * @param callableType The callable type (e.g., from a lambda or function)
     * @param protocol The protocol class that should have a `__call__` method
     * @param context Type evaluation context
     * @return true if the callable is compatible with the protocol's `__call__` signature
     */
    fun isCallableCompatibleWithCallProtocol(
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
}
