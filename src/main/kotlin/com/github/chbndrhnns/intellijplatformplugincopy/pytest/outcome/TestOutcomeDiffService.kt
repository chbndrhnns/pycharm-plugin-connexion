package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class TestOutcomeDiffService() {

    private val diffs = ConcurrentHashMap<String, OutcomeDiff>()

    fun put(key: String, diff: OutcomeDiff) {
        diffs[key] = diff
    }

    fun remove(key: String) {
        diffs.remove(key)
    }

    fun clearAll() {
        diffs.clear()
    }

    fun getAllKeys(): Set<String> = diffs.keys.toSet()

    fun get(key: String): OutcomeDiff? = diffs[key]

    fun findWithKey(baseLocationUrl: String, explicitKey: String?): Pair<OutcomeDiff, String>? {
        if (!explicitKey.isNullOrBlank()) {
            val exactExplicit = diffs[explicitKey]
            if (exactExplicit != null) return Pair(exactExplicit, explicitKey)
        }

        // Try exact match first
        val exact = diffs[baseLocationUrl]
        if (exact != null) return Pair(exact, baseLocationUrl)

        // Try prefix match (for parametrized tests)
        val prefixMatch = diffs.keys.firstOrNull { it.startsWith(baseLocationUrl) }
        if (prefixMatch != null) {
            val data = diffs[prefixMatch]
            if (data != null) return Pair(data, prefixMatch)
        }

        // Fallback: match by qualified name only (the part after ://)
        // This handles cases where the path in angle brackets differs
        val qNamePart = baseLocationUrl.substringAfter("://", "")
        if (qNamePart.isNotEmpty()) {
            val matchedKey = diffs.keys.firstOrNull { storedKey ->
                storedKey.substringAfter("://", "") == qNamePart
            }
            if (matchedKey != null) {
                val data = diffs[matchedKey]
                if (data != null) return Pair(data, matchedKey)
            }
        }

        return null
    }

    companion object {
        fun getInstance(project: Project): TestOutcomeDiffService =
            project.getService(TestOutcomeDiffService::class.java)
    }
}
