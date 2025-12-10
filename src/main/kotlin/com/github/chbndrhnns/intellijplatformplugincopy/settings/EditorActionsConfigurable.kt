package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class EditorActionsConfigurable : BoundConfigurable("Editor Actions") {
    private val settings = PluginSettingsState.instance().state

    override fun createPanel(): DialogPanel {
        return panel {
            row {
                checkBox("‘Copy Package Content’ context menu action")
                    .bindSelected(settings::enableCopyPackageContentAction)
            }
            row {
                checkBox("‘Copy Build Number’ context menu action")
                    .bindSelected(settings::enableCopyBuildNumberAction)
            }
        }
    }
}
