package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText

class PluginSettingsConfigurable : BoundConfigurable("BetterPy"), SearchableConfigurable {
    private val registry = FeatureRegistry.instance()
    private lateinit var stateSnapshot: FeatureStateSnapshot
    private var defaultParameterObjectBaseType: String = ""
    private var preferOwnTypesInUnionWrapping: Boolean = true
    private var includeStdlibInUnionWrapping: Boolean = true

    override fun getId(): String = "com.github.chbndrhnns.intellijplatformplugincopy.settings"

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        return createFilterableFeaturePanel { _ ->
            val rows = mutableListOf<RowMetadata>()
            val featuresByCategory = registry.getVisibleFeaturesByCategories()

            FeatureCategory.entries.forEach { category ->
                val features = featuresByCategory[category] ?: return@forEach
                if (features.isEmpty()) return@forEach

                group(category.displayName) {
                    if (category == FeatureCategory.ACTIONS) {
                        row("Default parameter object base type:") {
                            textField()
                                .bindText(::defaultParameterObjectBaseType)
                        }
                    }
                    if (category == FeatureCategory.TYPE_WRAPPING) {
                        row {
                            checkBox("Prefer project types in union wrapping")
                                .bindSelected(::preferOwnTypesInUnionWrapping)
                        }
                        row {
                            checkBox("Include standard library types in union wrapping")
                                .bindSelected(::includeStdlibInUnionWrapping)
                        }
                    }

                    features.forEach { feature ->
                        val row = featureRow(
                            feature,
                            getter = { stateSnapshot.isEnabled(feature.id) },
                            setter = { value -> stateSnapshot.setEnabled(feature.id, value) }
                        )
                        rows.add(row)

                        // Special handling for nested settings fields that aren't Boolean features
                        if (feature.id == "parameter-object-refactoring") {
                            row.row.visible(stateSnapshot.isEnabled("parameter-object-refactoring"))
                        }
                    }
                }
            }

            rows
        }.asDialogPanel()
    }

    override fun isModified(): Boolean {
        // Check both snapshot's internal modification tracking and parent's UI tracking
        val snapshotModified = ::stateSnapshot.isInitialized && stateSnapshot.isModified()
        val boundModified = super<BoundConfigurable>.isModified()

        // Manual checks
        val state = PluginSettingsState.instance().state
        val manualModified = defaultParameterObjectBaseType != state.parameterObject.defaultParameterObjectBaseType ||
                preferOwnTypesInUnionWrapping != state.preferOwnTypesInUnionWrapping ||
                includeStdlibInUnionWrapping != state.includeStdlibInUnionWrapping

        return snapshotModified || boundModified || manualModified
    }

    override fun apply() {
        super<BoundConfigurable>.apply()
        if (::stateSnapshot.isInitialized) {
            stateSnapshot.applyTo(registry)
            stateSnapshot = stateSnapshot.withNewBaseline()
            val stateComponent = PluginSettingsState.instance()
            val stateCopy = stateComponent.state.copy()

            // Apply manual settings
            stateCopy.parameterObject.defaultParameterObjectBaseType = defaultParameterObjectBaseType
            stateCopy.preferOwnTypesInUnionWrapping = preferOwnTypesInUnionWrapping
            stateCopy.includeStdlibInUnionWrapping = includeStdlibInUnionWrapping
            
            stateComponent.loadState(stateCopy)
        }
    }

    override fun reset() {
        if (::stateSnapshot.isInitialized) {
            stateSnapshot.reset()
            // Reset manual settings
            val state = PluginSettingsState.instance().state
            defaultParameterObjectBaseType = state.parameterObject.defaultParameterObjectBaseType
            preferOwnTypesInUnionWrapping = state.preferOwnTypesInUnionWrapping
            includeStdlibInUnionWrapping = state.includeStdlibInUnionWrapping
            
            super<BoundConfigurable>.reset()
        }
    }
}
