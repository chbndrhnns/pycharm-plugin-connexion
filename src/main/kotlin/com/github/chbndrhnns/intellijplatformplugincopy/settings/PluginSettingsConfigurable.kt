package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel

class PluginSettingsConfigurable : BoundConfigurable("BetterPy"), SearchableConfigurable {
    private val registry = FeatureRegistry.instance()
    private lateinit var stateSnapshot: FeatureStateSnapshot

    override fun getId(): String = "com.github.chbndrhnns.intellijplatformplugincopy.settings"

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        return createFilterableFeaturePanel { _ ->
            val rows = mutableListOf<RowMetadata>()

            row {
                label("Please select a category to configure settings.")
            }

            rows
        }.asDialogPanel()
    }

    override fun isModified(): Boolean {
        // Check both snapshot's internal modification tracking and parent's UI tracking
        val snapshotModified = ::stateSnapshot.isInitialized && stateSnapshot.isModified()
        val boundModified = super<BoundConfigurable>.isModified()
        return snapshotModified || boundModified
    }

    override fun apply() {
        super<BoundConfigurable>.apply()
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
            super<BoundConfigurable>.reset()
        }
    }
}
