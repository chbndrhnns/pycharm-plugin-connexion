package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.outcome

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class TestOutcomeDiffService() {

    private val diffs = ConcurrentHashMap<String, OutcomeDiff>()

    fun put(key: String, diff: OutcomeDiff) {
        LOG.debug("TestOutcomeDiffService.put: storing diff with key '$key', diff=$diff")
        diffs[key] = diff
        LOG.debug("TestOutcomeDiffService.put: total keys in service: ${diffs.keys.size}")
    }

    fun remove(key: String) {
        LOG.debug("TestOutcomeDiffService.remove: removing diff with key '$key'")
        diffs.remove(key)
    }

    fun clearAll() {
        LOG.debug("TestOutcomeDiffService.clearAll: clearing all diffs (had ${diffs.keys.size} entries)")
        diffs.clear()
    }

    fun getAllKeys(): Set<String> = diffs.keys.toSet()

    fun get(key: String): OutcomeDiff? {
        val result = diffs[key]
        LOG.debug("TestOutcomeDiffService.get: looking up key '$key', found=${result != null}")
        return result
    }

    fun findWithKey(baseLocationUrl: String, explicitKey: String?): Pair<OutcomeDiff, String>? {
        LOG.debug("TestOutcomeDiffService.findWithKey: searching for baseLocationUrl='$baseLocationUrl', explicitKey='$explicitKey'")
        
        if (!explicitKey.isNullOrBlank()) {
            val exactExplicit = diffs[explicitKey]
            if (exactExplicit != null) {
                LOG.debug("TestOutcomeDiffService.findWithKey: found exact match for explicit key '$explicitKey'")
                return Pair(exactExplicit, explicitKey)
            }
        }

        // Try exact match first
        val exact = diffs[baseLocationUrl]
        if (exact != null) {
            LOG.debug("TestOutcomeDiffService.findWithKey: found exact match for base URL '$baseLocationUrl'")
            return Pair(exact, baseLocationUrl)
        }

        // Try prefix match (for parametrized tests)
        val prefixMatch = diffs.keys.firstOrNull { it.startsWith(baseLocationUrl) }
        if (prefixMatch != null) {
            val data = diffs[prefixMatch]
            if (data != null) {
                LOG.debug("TestOutcomeDiffService.findWithKey: found prefix match '$prefixMatch' for base URL '$baseLocationUrl'")
                return Pair(data, prefixMatch)
            }
        }

        LOG.debug("TestOutcomeDiffService.findWithKey: no match found for base URL '$baseLocationUrl'")
        return null
    }

    fun findWithKeys(baseLocationUrls: List<String>, explicitKey: String?): Pair<OutcomeDiff, String>? {
        LOG.debug("TestOutcomeDiffService.findWithKeys: searching with ${baseLocationUrls.size} URL(s): $baseLocationUrls, explicitKey='$explicitKey'")
        for (url in baseLocationUrls) {
            val result = findWithKey(url, explicitKey)
            if (result != null) {
                LOG.debug("TestOutcomeDiffService.findWithKeys: found match with URL '$url'")
                return result
            }
        }
        LOG.debug("TestOutcomeDiffService.findWithKeys: no match found for any URL")
        return null
    }

    companion object {
        private val LOG = Logger.getInstance(TestOutcomeDiffService::class.java)
        
        fun getInstance(project: Project): TestOutcomeDiffService =
            project.getService(TestOutcomeDiffService::class.java)
    }
}
