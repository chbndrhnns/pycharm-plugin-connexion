package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.ParameterObjectBaseType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected

class ParameterObjectConfigurable : BoundConfigurable("Parameter Object") {
    private val settings = PluginSettingsState.instance().state

    override fun createPanel(): DialogPanel {
        return createFilterableFeaturePanel { _, searchTerm ->
            group("Refactoring Actions") {
                row {
                    val label = "Enable ‘Introduce Parameter Object’ refactoring"
                    if (searchTerm.isEmpty() || label.contains(searchTerm, ignoreCase = true)) {
                        checkBox(label)
                            .bindSelected(settings::enableIntroduceParameterObjectRefactoringAction)
                    }
                }
                row {
                    val label = "Enable ‘Inline Parameter Object’ refactoring"
                    if (searchTerm.isEmpty() || label.contains(searchTerm, ignoreCase = true)) {
                        checkBox(label)
                            .bindSelected(settings::enableInlineParameterObjectRefactoringAction)
                    }
                }
            }
            group("Parameter Object Settings") {
                row("Default base type:") {
                    val label = "Default base type:"
                    if (searchTerm.isEmpty() || label.contains(searchTerm, ignoreCase = true)) {
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
        }.asDialogPanel()
    }
}
