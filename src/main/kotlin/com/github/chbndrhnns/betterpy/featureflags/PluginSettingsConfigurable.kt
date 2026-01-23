package com.github.chbndrhnns.betterpy.featureflags

import com.github.chbndrhnns.betterpy.featureflags.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected

class PluginSettingsConfigurable : BoundConfigurable("BetterPy"), SearchableConfigurable {
    private val registry = FeatureRegistry.instance()
    private lateinit var stateSnapshot: FeatureStateSnapshot
    private var preferOwnTypesInUnionWrapping: Boolean = true
    private var includeStdlibInUnionWrapping: Boolean = true

    override fun getId(): String = "com.github.chbndrhnns.betterpy.featureflags"

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        // Initialize manual settings from state
        val state = PluginSettingsState.instance().state
        preferOwnTypesInUnionWrapping = state.preferOwnTypesInUnionWrapping
        includeStdlibInUnionWrapping = state.includeStdlibInUnionWrapping

        return createFilterableFeaturePanel { _ ->
            val rows = mutableListOf<RowMetadata>()
            val featuresByCategory = registry.getVisibleFeaturesByCategories()

            FeatureCategory.entries.forEach { category ->
                val features = featuresByCategory[category] ?: return@forEach
                if (features.isEmpty()) return@forEach

                group(category.displayName) {
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

                    // Filter out parameter object features - they're in a separate configurable
                    val filteredFeatures = if (category == FeatureCategory.ACTIONS) {
                        features.filter { !it.id.startsWith("parameter-object-") }
                    } else features

                    filteredFeatures.forEach { feature ->
                        val row = featureRow(
                            feature,
                            getter = { stateSnapshot.isEnabled(feature.id) },
                            setter = { value -> stateSnapshot.setEnabled(feature.id, value) }
                        )
                        rows.add(row)
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
        val manualModified = preferOwnTypesInUnionWrapping != state.preferOwnTypesInUnionWrapping ||
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
            preferOwnTypesInUnionWrapping = state.preferOwnTypesInUnionWrapping
            includeStdlibInUnionWrapping = state.includeStdlibInUnionWrapping
            
            super<BoundConfigurable>.reset()
        }
    }
}
