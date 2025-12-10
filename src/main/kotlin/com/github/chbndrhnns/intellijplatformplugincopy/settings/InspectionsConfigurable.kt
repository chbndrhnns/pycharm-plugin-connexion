package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class InspectionsConfigurable : BoundConfigurable("Inspections") {
    private val settings = PluginSettingsState.instance().state

    override fun createPanel(): DialogPanel {
        return panel {
            row {
                checkBox("‘__all__’ export inspection")
                    .bindSelected(settings::enablePyMissingInDunderAllInspection)
            }
            row {
                checkBox("‘Dataclass missing’ inspection")
                    .bindSelected(settings::enableDataclassMissingInspection)
            }
            row {
                checkBox("‘Private module import’ inspection")
                    .bindSelected(settings::enablePrivateModuleImportInspection)
            }
        }
    }
}
