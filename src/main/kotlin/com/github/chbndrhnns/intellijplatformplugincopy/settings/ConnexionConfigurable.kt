package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class ConnexionConfigurable : BoundConfigurable("Connexion") {
    private val settings = PluginSettingsState.instance().state

    override fun createPanel(): DialogPanel {
        return panel {
            row {
                checkBox("Enable Connexion inspections")
                    .bindSelected(settings::enableConnexionInspections)
            }
            row {
                checkBox("Enable Connexion completion & navigation")
                    .bindSelected(settings::enableConnexionCompletion)
            }
        }
    }
}
