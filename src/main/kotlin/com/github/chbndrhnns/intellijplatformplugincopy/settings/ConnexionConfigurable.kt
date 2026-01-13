package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel

class ConnexionConfigurable : BoundConfigurable("Connexion") {
    private val registry = FeatureRegistry.instance()
    private lateinit var stateSnapshot: FeatureStateSnapshot

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        return createFilterableFeaturePanel { onLoggingChanged ->
            val rows = mutableListOf<RowMetadata>()

            group("Connexion") {
                registry.getFeature("connexion-inspections")?.let { feature ->
                    rows.add(
                        featureRow(
                            feature,
                            getter = { stateSnapshot.isEnabled(feature.id) },
                            setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                            onLoggingChanged = onLoggingChanged
                        )
                    )
                }
                registry.getFeature("connexion-completion")?.let { feature ->
                    rows.add(
                        featureRow(
                            feature,
                            getter = { stateSnapshot.isEnabled(feature.id) },
                            setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                            onLoggingChanged = onLoggingChanged
                        )
                    )
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
