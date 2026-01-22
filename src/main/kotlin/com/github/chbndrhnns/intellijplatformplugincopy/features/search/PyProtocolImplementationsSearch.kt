package com.github.chbndrhnns.intellijplatformplugincopy.features.search

import com.github.chbndrhnns.intellijplatformplugincopy.core.index.PyClassMembersFileIndex
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.concurrent.ConcurrentHashMap
import com.jetbrains.python.codeInsight.typing.isProtocol as builtInIsProtocol

object PyProtocolImplementationsSearch {

    // Cache for protocol implementation results
    private val cache = ConcurrentHashMap<CacheKey, CachedResult>()

    // Cache TTL in milliseconds (30 seconds)
    private const val CACHE_TTL_MS = 30_000L

    data class CacheKey(
        val protocolQName: String,
        val scopeHash: Int,
        val modificationStamp: Long
    )

    data class CachedResult(
        val implementations: Collection<PyClass>,
        val timestamp: Long
    )

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

    /**
     * Searches for classes that structurally implement the given Protocol.
     * Results are cached for performance.
     *
     * @param protocol The Protocol class to find implementations for
     * @param scope The search scope
     * @param context Type evaluation context
     * @return Collection of classes implementing the Protocol
     */
    fun search(
        protocol: PyClass, scope: GlobalSearchScope, context: TypeEvalContext
    ): Collection<PyClass> {
        val qName = protocol.qualifiedName
        if (qName != null) {
            val modStamp = protocol.containingFile?.modificationStamp ?: 0L
            val key = CacheKey(qName, scope.hashCode(), modStamp)

            val cached = cache[key]
            val now = System.currentTimeMillis()

            // Return cached result if valid and not expired
            if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS) {
                // Validate cached classes are still valid
                if (cached.implementations.all { it.isValid }) {
                    return cached.implementations
                }
            }

            // Compute and cache
            val result = searchInternal(protocol, scope, context)
            cache[key] = CachedResult(result, now)
            return result
        }

        return searchInternal(protocol, scope, context)
    }

    /**
     * Internal search implementation without caching.
     */
    private fun searchInternal(
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
            if (PyTestDetection.isTestClass(candidate)) continue // Exclude test classes

            val candidateType = context.getType(candidate) as? PyClassType ?: continue

            if (PyProtocolTypeMatcher.matchesProtocol(protocolType, candidateType, context)) {
                implementations.add(candidate)
            }
        }

        return implementations
    }

    /**
     * Invalidates the entire cache.
     * Called when Python files are modified.
     */
    fun invalidateCache() {
        cache.clear()
    }

    /**
     * Invalidates cache entries for a specific protocol.
     *
     * @param protocolQName The qualified name of the protocol to invalidate
     */
    fun invalidateCacheFor(protocolQName: String) {
        cache.keys.removeIf { it.protocolQName == protocolQName }
    }

    private fun getProtocolRequiredMembers(protocol: PyClass): List<String> {
        val members = mutableListOf<String>()

        // Get methods
        for (method in protocol.methods) {
            val name = method.name ?: continue
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

    /**
     * Finds candidate classes that might implement the Protocol.
     * Uses [PyClassMembersFileIndex] for efficient O(1) lookup of files containing classes with the member,
     * then resolves classes from those files. Falls back to iterating all classes if the index is empty.
     */
    private fun findCandidateClasses(
        project: Project, scope: GlobalSearchScope, memberName: String
    ): Collection<PyClass> {
        // Try to use the optimized file-based index first
        val filesWithMember = PyClassMembersFileIndex.findFilesWithMember(memberName, project, scope)
        if (filesWithMember.isNotEmpty()) {
            val candidates = mutableSetOf<PyClass>()
            val psiManager = PsiManager.getInstance(project)

            for (vFile in filesWithMember) {
                ProgressManager.checkCanceled()
                val pyFile = psiManager.findFile(vFile) as? PyFile ?: continue

                // Find classes in this file that have the required member
                for (pyClass in pyFile.topLevelClasses) {
                    if (pyClass.findMethodByName(memberName, false, null) != null ||
                        pyClass.findClassAttribute(memberName, false, null) != null
                    ) {
                        candidates.add(pyClass)
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                return candidates
            }
        }

        // Fallback to iterating all classes (for backward compatibility or when index is empty)
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

    /**
     * Checks if a class is a test class (should be excluded from protocol implementations).
     * Delegates to [PyTestDetection.isTestClass].
     */
    fun isTestClass(pyClass: PyClass): Boolean = PyTestDetection.isTestClass(pyClass)

    /**
     * Checks if a function is a test function (should be excluded from protocol implementations).
     * Delegates to [PyTestDetection.isTestFunction].
     */
    fun isTestFunction(pyFunction: PyFunction): Boolean = PyTestDetection.isTestFunction(pyFunction)

    /**
     * Checks if a protocol only requires a `__call__` method (i.e., it's a "callable protocol").
     * Delegates to [PyCallableProtocolMatcher.isCallableOnlyProtocol].
     */
    fun isCallableOnlyProtocol(protocol: PyClass, context: TypeEvalContext): Boolean =
        PyCallableProtocolMatcher.isCallableOnlyProtocol(protocol, context)

    /**
     * Gets the `__call__` method from a protocol, if it exists.
     * Delegates to [PyCallableProtocolMatcher.getCallMethod].
     */
    fun getCallMethod(protocol: PyClass): PyFunction? = PyCallableProtocolMatcher.getCallMethod(protocol)

    /**
     * Checks if a callable type (lambda, function) is compatible with a protocol's `__call__` method.
     * Delegates to [PyCallableProtocolMatcher.isCallableCompatible].
     */
    fun isCallableCompatibleWithCallProtocol(
        callableType: PyCallableType,
        protocol: PyClass,
        context: TypeEvalContext
    ): Boolean = PyCallableProtocolMatcher.isCallableCompatible(callableType, protocol, context)
}
