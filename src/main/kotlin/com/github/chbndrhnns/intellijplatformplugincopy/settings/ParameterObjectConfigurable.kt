package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.ParameterObjectBaseType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel

class ParameterObjectConfigurable : BoundConfigurable("Parameter Object") {
    private val settings = PluginSettingsState.instance().state

    override fun createPanel(): DialogPanel {
        return panel {
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
        }
    }
}
