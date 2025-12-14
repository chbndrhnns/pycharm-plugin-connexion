package com.github.chbndrhnns.intellijplatformplugincopy.search

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.types.PyClassType
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

        // 1. Check methods
        for (method in protocolClass.methods) {
            val name = method.name ?: continue
            if (shouldIgnoreMember(name)) continue

            // Check if candidate has this method
            // true = inherited
            if (candidateClass.findMethodByName(name, true, context) == null) {
                // Check if implemented as attribute (e.g. Callable)
                if (candidateClass.findClassAttribute(
                        name,
                        true,
                        context
                    ) == null && candidateClass.findInstanceAttribute(name, true) == null
                ) {
                    return false
                }
            }
        }

        // 2. Check attributes
        for (attr in protocolClass.classAttributes) {
            val name = attr.name ?: continue
            if (shouldIgnoreMember(name)) continue

            if (candidateClass.findClassAttribute(name, true, context) == null && candidateClass.findInstanceAttribute(
                    name,
                    true
                ) == null
            ) {
                return false
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
}
