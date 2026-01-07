package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel

/**
 * Settings configurable for editor actions and related features.
 * Uses [FeatureRegistry] to dynamically build the UI with maturity indicators and YouTrack links.
 * Includes a maturity filter panel at the top to filter features by status.
 */
class EditorActionsConfigurable : BoundConfigurable("Editor Actions") {
    private val registry = FeatureRegistry.instance()

    override fun createPanel(): DialogPanel {
        return createFilterableFeaturePanel { visibleMaturities, searchTerm, onLoggingChanged ->
            // Copy/Clipboard Actions
            group("Copy/Clipboard Actions") {
                registry.getFeature("copy-package-content")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("copy-build-number")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("copy-block-with-dependencies")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("copy-pytest-node-ids")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("copy-pytest-node-id-from-editor")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("copy-fqns")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("copy-stacktrace")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
            }

            // Refactoring Actions
            group("Refactoring Actions") {
                registry.getFeature("introduce-parameter-object")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("inline-parameter-object")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
            }

            // Pytest Actions
            group("Pytest Actions") {
                registry.getFeature("jump-to-pytest-node-in-test-tree")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("toggle-pytest-skip-from-test-tree")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
            }

            // Completion & Reference Contributors
            group("Completion & References") {
                registry.getFeature("return-completion")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("mock-patch-reference")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("filter-warnings-reference")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("pytest-identifier-search-everywhere")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
            }

            // Other Editor Features
            group("Other Editor Features") {
                registry.getFeature("toggle-type-alias")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("export-symbol-to-target")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("newtype-typevar-paramspec-rename")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("type-annotation-usage-filtering")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
                registry.getFeature("python-message-console-filter")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm, onLoggingChanged = onLoggingChanged) }
            }
        }.asDialogPanel()
    }
}
