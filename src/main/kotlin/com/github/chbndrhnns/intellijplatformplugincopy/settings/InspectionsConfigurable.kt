package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class InspectionsConfigurable : BoundConfigurable("Inspections"), Configurable.WithEpDependencies {
    private val settings = PluginSettingsState.instance().state

    override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
        return listOf(ExtensionPointName.create<Any>("com.intellij.localInspection"))
    }

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
            row {
                checkBox("‘Abstract method not implemented in child classes’ inspection")
                    .bindSelected(settings::enableAbstractMethodNotImplementedInspection)
            }
            row {
                checkBox("'Unresolved reference as error' inspection")
                    .bindSelected(settings::enableUnresolvedReferenceAsErrorInspection)
            }
        }
    }
}
