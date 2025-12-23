package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.ParameterObjectBaseType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class ParameterObjectConfigurable : BoundConfigurable("Parameter Object") {
    private val settings = PluginSettingsState.instance().state

    override fun createPanel(): DialogPanel {
        return panel {
            group("Refactoring Actions") {
                row {
                    checkBox("Enable ‘Introduce Parameter Object’ refactoring")
                        .bindSelected(settings::enableIntroduceParameterObjectRefactoringAction)
                }
                row {
                    checkBox("Enable ‘Inline Parameter Object’ refactoring")
                        .bindSelected(settings::enableInlineParameterObjectRefactoringAction)
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
        }
    }
}
