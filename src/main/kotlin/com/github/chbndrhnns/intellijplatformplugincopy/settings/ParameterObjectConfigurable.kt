package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComboBox

class ParameterObjectConfigurable : BoundConfigurable("Parameter Object Refactoring"), SearchableConfigurable {
    private val registry = FeatureRegistry.instance()
    private lateinit var stateSnapshot: FeatureStateSnapshot
    private var defaultParameterObjectBaseType: String = ""
    private lateinit var baseTypeComboBox: Cell<JComboBox<String>>

    override fun getId(): String = "com.github.chbndrhnns.intellijplatformplugincopy.settings.parameterobject"

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        // Initialize manual settings from state
        val state = PluginSettingsState.instance().state
        defaultParameterObjectBaseType = state.parameterObject.defaultParameterObjectBaseType

        return panel {
            // Get parameter object features
            val features = registry.getVisibleFeaturesByCategories()[FeatureCategory.ACTIONS]
                ?.filter { it.id.startsWith("parameter-object-") } ?: emptyList()

            features.forEach { feature ->
                featureRow(
                    feature,
                    getter = { stateSnapshot.isEnabled(feature.id) },
                    setter = { value -> stateSnapshot.setEnabled(feature.id, value) }
                )
            }

            row("Default parameter object base type:") {
                baseTypeComboBox = comboBox(listOf("dataclass", "NamedTuple", "TypedDict", "pydantic.BaseModel"))
                    .also { it.component.selectedItem = defaultParameterObjectBaseType }
            }
        }
    }

    override fun isModified(): Boolean {
        // Check snapshot's internal modification tracking
        val snapshotModified = ::stateSnapshot.isInitialized && stateSnapshot.isModified()
        val boundModified = super<BoundConfigurable>.isModified()

        // Manual checks
        val state = PluginSettingsState.instance().state

        // Check comboBox if initialized, otherwise check the property
        val baseTypeModified = if (::baseTypeComboBox.isInitialized) {
            baseTypeComboBox.component.selectedItem != state.parameterObject.defaultParameterObjectBaseType
        } else {
            defaultParameterObjectBaseType != state.parameterObject.defaultParameterObjectBaseType
        }

        return snapshotModified || boundModified || baseTypeModified
    }

    override fun apply() {
        super<BoundConfigurable>.apply()
        if (::stateSnapshot.isInitialized) {
            // Read from comboBox if initialized
            if (::baseTypeComboBox.isInitialized) {
                defaultParameterObjectBaseType = (baseTypeComboBox.component.selectedItem as? String) ?: "dataclass"
            }

            stateSnapshot.applyTo(registry)
            stateSnapshot = stateSnapshot.withNewBaseline()
            val stateComponent = PluginSettingsState.instance()
            val stateCopy = stateComponent.state.copy()

            // Apply manual settings
            stateCopy.parameterObject.defaultParameterObjectBaseType = defaultParameterObjectBaseType

            stateComponent.loadState(stateCopy)
        }
    }

    override fun reset() {
        if (::stateSnapshot.isInitialized) {
            stateSnapshot.reset()
            // Reset manual settings
            val state = PluginSettingsState.instance().state
            defaultParameterObjectBaseType = state.parameterObject.defaultParameterObjectBaseType

            // Update comboBox if initialized
            if (::baseTypeComboBox.isInitialized) {
                baseTypeComboBox.component.selectedItem = defaultParameterObjectBaseType
            }

            super<BoundConfigurable>.reset()
        }
    }
}
