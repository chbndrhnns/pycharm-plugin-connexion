package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected

class ConnexionConfigurable : BoundConfigurable("Connexion") {
    private val settings = PluginSettingsState.instance().state

    override fun createPanel(): DialogPanel {
        return createFilterableFeaturePanel { _, searchTerm ->
            group("Connexion") {
                row {
                    val label = "Enable Connexion inspections"
                    if (searchTerm.isEmpty() || label.contains(searchTerm, ignoreCase = true)) {
                        checkBox(label)
                            .bindSelected(settings::enableConnexionInspections)
                    }
                }
                row {
                    val label = "Enable Connexion completion & navigation"
                    if (searchTerm.isEmpty() || label.contains(searchTerm, ignoreCase = true)) {
                        checkBox(label)
                            .bindSelected(settings::enableConnexionCompletion)
                    }
                }
            }
        }.asDialogPanel()
    }
}
