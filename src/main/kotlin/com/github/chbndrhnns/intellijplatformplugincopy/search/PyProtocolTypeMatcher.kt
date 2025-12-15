package com.github.chbndrhnns.intellijplatformplugincopy.search

import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Handles type matching logic for protocol compatibility checking.
 * Performs structural subtyping checks between protocols and candidate classes.
 */
object PyProtocolTypeMatcher {

    private val IGNORED_PROTOCOL_MEMBERS = setOf(
        "__init__", "__new__", "__slots__", "__class_getitem__", "__init_subclass__"
    )

    /**
     * Checks if a candidate class structurally matches a protocol.
     * Performs manual structural subtyping check for methods and attributes.
     */
    fun matchesProtocol(
        protocol: PyClassType,
        candidate: PyClassType,
        context: TypeEvalContext
    ): Boolean {
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
    fun isMethodCompatible(
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

    /**
     * Determines if a member name should be ignored during protocol matching.
     */
    fun shouldIgnoreMember(name: String): Boolean {
        return (name.startsWith("_") && !name.startsWith("__")) || name in IGNORED_PROTOCOL_MEMBERS
    }
}
