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
    private lateinit var stateSnapshot: FeatureStateSnapshot

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        return createFilterableFeaturePanel { onLoggingChanged ->
            val rows = mutableListOf<RowMetadata>()

            // Copy/Clipboard Actions
            group("Copy/Clipboard Actions") {
                registry.getFeature("copy-package-content")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("copy-build-number")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("copy-block-with-dependencies")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("copy-pytest-node-ids")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("copy-pytest-node-id-from-editor")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("copy-fqns")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("copy-stacktrace")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            // Refactoring Actions
            group("Refactoring Actions") {
                registry.getFeature("introduce-parameter-object")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("inline-parameter-object")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            // Pytest Actions
            group("Pytest Actions") {
                registry.getFeature("jump-to-pytest-node-in-test-tree")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("toggle-pytest-skip-from-test-tree")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            // Completion & Reference Contributors
            group("Completion & References") {
                registry.getFeature("return-completion")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("mock-patch-reference")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("filter-warnings-reference")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("pytest-identifier-search-everywhere")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            // Other Editor Features
            group("Other Editor Features") {
                registry.getFeature("toggle-type-alias")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("export-symbol-to-target")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("newtype-typevar-paramspec-rename")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("type-annotation-usage-filtering")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("python-message-console-filter")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            rows
        }.asDialogPanel()
    }

    override fun isModified(): Boolean {
        // Check both snapshot's internal modification tracking and parent's UI tracking
        val snapshotModified = ::stateSnapshot.isInitialized && stateSnapshot.isModified()
        val boundModified = super.isModified()
        return snapshotModified || boundModified
    }

    override fun apply() {
        super.apply()
        if (::stateSnapshot.isInitialized) {
            stateSnapshot.applyTo(registry)
            stateSnapshot = stateSnapshot.withNewBaseline()
            val stateComponent = PluginSettingsState.instance()
            val stateCopy = stateComponent.state.copy()
            stateComponent.loadState(stateCopy)
        }
    }

    override fun reset() {
        if (::stateSnapshot.isInitialized) {
            stateSnapshot.reset()
            super.reset()
        }
    }
}
