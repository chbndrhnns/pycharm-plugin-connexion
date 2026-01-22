package com.github.chbndrhnns.intellijplatformplugincopy.featureflags

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Service for managing debug logging for plugin features.
 * Provides functionality to copy log categories to clipboard.
 */
@Service
class FeatureLoggingService {

    private val LOG = Logger.getInstance(FeatureLoggingService::class.java)

    /**
     * Copies the logging categories for the given feature to the system clipboard.
     * Each category is appended with `:trace:separate` as required for log configuration.
     *
     * @param feature The feature whose logging categories should be copied
     */
    fun copyLoggingCategoriesToClipboard(feature: FeatureRegistry.FeatureInfo) {
        if (feature.loggingCategories.isEmpty()) return

        val formattedCategories = feature.loggingCategories.joinToString("\n") { category ->
            "$category:trace:separate"
        }

        val selection = StringSelection(formattedCategories)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)

        LOG.info(
            "Copied logging categories to clipboard for feature '${feature.displayName}' (${feature.id}): ${
                feature.loggingCategories.joinToString(", ")
            }"
        )
    }

    companion object {
        @JvmStatic
        fun instance(): FeatureLoggingService = service()
    }
}
