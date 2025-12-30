package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class ImportsProjectViewConfigurable : BoundConfigurable("Imports & Project View"), Configurable.WithEpDependencies {
    private val settings = PluginSettingsState.instance().state

    override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
        return listOf(
            ExtensionPointName.create<Any>("Pythonid.canonicalPathProvider"),
            ExtensionPointName.create<Any>("Pythonid.importCandidateProvider"),
            ExtensionPointName.create<Any>("com.intellij.lang.psiStructureViewFactory")
        )
    }

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
            row {
                checkBox("Hide transient dependency imports (only show direct dependencies)")
                    .bindSelected(settings::enableHideTransientImports)
            }
        }
    }
}
