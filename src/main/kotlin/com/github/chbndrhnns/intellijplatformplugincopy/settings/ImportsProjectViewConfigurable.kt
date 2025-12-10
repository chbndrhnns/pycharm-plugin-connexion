package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class ImportsProjectViewConfigurable : BoundConfigurable("Imports & Project View") {
    private val settings = PluginSettingsState.instance().state

    override fun createPanel(): DialogPanel {
        return panel {
            row {
                checkBox("‘Restore Source Root Prefix’ in imports")
                    .bindSelected(settings::enableRestoreSourceRootPrefix)
            }
            row {
                checkBox("‘Prefer relative imports’ in auto-import")
                    .bindSelected(settings::enableRelativeImportPreference)
            }
            row {
                checkBox("‘Show Private Members’ filter in Structure View")
                    .bindSelected(settings::enableStructureViewPrivateMembersFilter)
            }
        }
    }
}
