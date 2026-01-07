package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger

/**
 * Service for managing debug logging for plugin features.
 * Uses LogLevelConfigurationManager to enable/disable debug logs for feature-specific categories.
 */
@Service
class FeatureLoggingService {

    private val logManager: LogLevelConfigurationManager
        get() = LogLevelConfigurationManager.getInstance()

    private val LOG = Logger.getInstance(FeatureLoggingService::class.java)

    /**
     * Enables debug logging for all categories associated with the given feature.
     *
     * @param feature The feature whose logging categories should be enabled
     */
    fun enableLogging(feature: FeatureRegistry.FeatureInfo) {
        if (feature.loggingCategories.isEmpty()) return

        val categories = feature.loggingCategories.map { category ->
            LogCategory(category, DebugLogLevel.DEBUG)
        }
        logManager.addCategories(categories)
        LOG.warn(
            "Debug logging enabled for feature '${feature.displayName}' (${feature.id}): ${
                feature.loggingCategories.joinToString(
                    ", "
                )
            }"
        )
    }

    /**
     * Disables debug logging for all categories associated with the given feature.
     *
     * @param feature The feature whose logging categories should be disabled
     */
    fun disableLogging(feature: FeatureRegistry.FeatureInfo) {
        if (feature.loggingCategories.isEmpty()) return

        val currentCategories = logManager.state.categories.toMutableList()
        currentCategories.removeAll { logCategory ->
            feature.loggingCategories.contains(logCategory.category)
        }
        logManager.setCategories(currentCategories)
        LOG.warn(
            "Debug logging disabled for feature '${feature.displayName}' (${feature.id}): ${
                feature.loggingCategories.joinToString(
                    ", "
                )
            }"
        )
    }

    /**
     * Checks if debug logging is enabled for any of the feature's categories.
     *
     * @param feature The feature to check
     * @return true if at least one of the feature's logging categories is currently enabled
     */
    fun isLoggingEnabled(feature: FeatureRegistry.FeatureInfo): Boolean {
        if (feature.loggingCategories.isEmpty()) return false

        val enabledCategories = logManager.state.categories.map { it.category }.toSet()
        return feature.loggingCategories.any { category ->
            enabledCategories.contains(category)
        }
    }

    companion object {
        @JvmStatic
        fun instance(): FeatureLoggingService = service()
    }
}
