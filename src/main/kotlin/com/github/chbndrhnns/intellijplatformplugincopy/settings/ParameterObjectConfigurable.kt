package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.ParameterObjectBaseType
import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem

class ParameterObjectConfigurable : BoundConfigurable("Parameter Object") {
    private val registry = FeatureRegistry.instance()
    private val settings = PluginSettingsState.instance().state
    private lateinit var stateSnapshot: FeatureStateSnapshot

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        return createFilterableFeaturePanel { onLoggingChanged ->
            val rows = mutableListOf<RowMetadata>()

            group("Refactoring Actions") {
                registry.getFeature("parameter-object-refactoring")?.let { feature ->
                    rows.add(featureRow(
                        feature,
                        getter = { stateSnapshot.isEnabled(feature.id) },
                        setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                    ))
                }
            }
            group("Parameter Object Settings") {
                row("Default base type:") {
                    comboBox(ParameterObjectBaseType.entries)
                        .bindItem(
                            getter = { ParameterObjectBaseType.fromDisplayName(settings.defaultParameterObjectBaseType) },
                            setter = {
                                settings.defaultParameterObjectBaseType =
                                    it?.displayName ?: ParameterObjectBaseType.default().displayName
                            }
                        )
                        .comment("The default base type used when introducing a parameter object")
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
