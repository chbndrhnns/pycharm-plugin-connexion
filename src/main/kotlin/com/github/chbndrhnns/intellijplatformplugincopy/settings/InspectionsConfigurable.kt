package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel

/**
 * Settings configurable for inspection-related features.
 * Uses [FeatureRegistry] to dynamically build the UI with maturity indicators and YouTrack links.
 * Includes a maturity filter panel at the top to filter features by status.
 *
 * Implements proper copy-on-edit state management.
 */
class InspectionsConfigurable : BoundConfigurable("Inspections"), Configurable.WithEpDependencies {
    private val registry = FeatureRegistry.instance()
    private lateinit var stateSnapshot: FeatureStateSnapshot

    override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
        return listOf(ExtensionPointName.create<Any>("com.intellij.localInspection"))
    }

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        return createFilterableFeaturePanel { onLoggingChanged ->
            val rows = mutableListOf<RowMetadata>()

            // All inspections are in the INSPECTIONS category
            group("Code Quality Inspections") {
                registry.getFeature("missing-in-dunder-all-inspection")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                        onLoggingChanged = onLoggingChanged
                    ))
                }
                registry.getFeature("dataclass-missing-inspection")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                        onLoggingChanged = onLoggingChanged
                    ))
                }
                registry.getFeature("private-module-import-inspection")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                        onLoggingChanged = onLoggingChanged
                    ))
                }
                registry.getFeature("abstract-method-not-implemented-inspection")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                        onLoggingChanged = onLoggingChanged
                    ))
                }
                registry.getFeature("constant-final-inspection")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                        onLoggingChanged = onLoggingChanged
                    ))
                }
                registry.getFeature("shadowing-stdlib-module-inspection")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                        onLoggingChanged = onLoggingChanged
                    ))
                }
                registry.getFeature("unresolved-reference-as-error-inspection")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                        onLoggingChanged = onLoggingChanged
                    ))
                }
            }

            group("Test Inspections") {
                registry.getFeature("pytest-failed-line-inspection")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                        onLoggingChanged = onLoggingChanged
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
