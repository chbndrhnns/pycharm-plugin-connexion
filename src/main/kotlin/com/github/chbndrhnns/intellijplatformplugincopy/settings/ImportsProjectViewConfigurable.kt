package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected

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
        return createFilterableFeaturePanel { _, searchTerm, _ ->
            group("Imports") {
                row {
                    val label = "‘Restore Source Root Prefix’ in imports"
                    if (searchTerm.isEmpty() || label.contains(searchTerm, ignoreCase = true)) {
                        checkBox(label)
                            .bindSelected(settings::enableRestoreSourceRootPrefix)
                    }
                }
                row {
                    val label = "‘Prefer relative imports’ in auto-import"
                    if (searchTerm.isEmpty() || label.contains(searchTerm, ignoreCase = true)) {
                        checkBox(label)
                            .bindSelected(settings::enableRelativeImportPreference)
                    }
                }
                row {
                    val label = "Hide transient dependency imports (only show direct dependencies)"
                    if (searchTerm.isEmpty() || label.contains(searchTerm, ignoreCase = true)) {
                        checkBox(label)
                            .bindSelected(settings::enableHideTransientImports)
                    }
                }
            }
            group("Project View") {
                row {
                    val label = "‘Show Private Members’ filter in Structure View"
                    if (searchTerm.isEmpty() || label.contains(searchTerm, ignoreCase = true)) {
                        checkBox(label)
                            .bindSelected(settings::enableStructureViewPrivateMembersFilter)
                    }
                }
            }
        }.asDialogPanel()
    }
}
