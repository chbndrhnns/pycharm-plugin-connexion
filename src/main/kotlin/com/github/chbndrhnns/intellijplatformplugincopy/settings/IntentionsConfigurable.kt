package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel

/**
 * Settings configurable for intention-related features.
 * Uses [FeatureRegistry] to dynamically build the UI with maturity indicators and YouTrack links.
 * Includes a maturity filter panel at the top to filter features by status.
 *
 * Implements proper copy-on-edit state management:
 * - UI binds to a temporary state snapshot
 * - Changes are not persisted until Apply is clicked
 * - Reset restores the original persistent state
 * - Cancel discards all changes
 */
class IntentionsConfigurable : BoundConfigurable("Intentions"), Configurable.WithEpDependencies {
    private val registry = FeatureRegistry.instance()
    private lateinit var stateSnapshot: FeatureStateSnapshot

    override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
        return emptyList()
    }

    override fun createPanel(): DialogPanel {
        // Create snapshot of current persistent state
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        return createFilterableFeaturePanel { onLoggingChanged ->
            val rows = mutableListOf<RowMetadata>()

            // Type Wrapping/Unwrapping
            group("Type Wrapping/Unwrapping") {
                registry.getFeature("wrap-with-expected-type")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("wrap-items-with-expected-type")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("unwrap-to-expected-type")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("unwrap-items-to-expected-type")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("introduce-custom-type-from-stdlib")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            // Parameter & Argument
            group("Parameter & Argument") {
                registry.getFeature("populate-arguments")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("make-parameter-optional")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("make-parameter-mandatory")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("create-local-variable")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            // Code Structure
            group("Code Structure") {
                registry.getFeature("dict-access")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("change-visibility")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("unexport-symbol")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("callable-to-protocol")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("add-exception-capture")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("wrap-exceptions-with-parentheses")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("strip-signature-type-annotations")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("toggle-type-alias")?.let { feature ->
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
            }

            // Abstract Methods
            group("Abstract Methods") {
                registry.getFeature("implement-abstract-method-in-child-classes")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("make-member-abstract-in-abstract-class")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            // Pytest
            group("Pytest") {
                registry.getFeature("toggle-pytest-skip")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("parametrize-pytest-test")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("convert-pytest-param")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("mock-type-provider")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("wrap-test-in-class")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            // Filters & Suppressors
            group("Filters & Suppressors") {
                registry.getFeature("suppress-signature-change-intention")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
                registry.getFeature("rename-to-self-filter")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }

            // Other
            group("Other") {
                registry.getFeature("package-run-configuration")?.let { feature ->
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
        // We need to check both:
        // 1. Our snapshot's internal modification tracking
        // 2. The parent BoundConfigurable's modification tracking (for UI changes)
        val snapshotModified = ::stateSnapshot.isInitialized && stateSnapshot.isModified()
        val boundModified = super.isModified()
        return snapshotModified || boundModified
    }

    override fun apply() {
        // First let BoundConfigurable handle any UI-bound components
        super.apply()

        if (::stateSnapshot.isInitialized) {
            stateSnapshot.applyTo(registry)
            stateSnapshot = stateSnapshot.withNewBaseline()

            // Force persistence by creating a new state object
            // IntelliJ's PersistentStateComponent detects changes by object reference
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
