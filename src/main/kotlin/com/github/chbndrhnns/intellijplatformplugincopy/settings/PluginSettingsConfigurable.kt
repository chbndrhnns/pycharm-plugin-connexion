package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class PluginSettingsConfigurable : BoundConfigurable("DDD Toolkit"), SearchableConfigurable {
    private val settings = PluginSettingsState.instance().state

    override fun getId(): String = "com.github.chbndrhnns.intellijplatformplugincopy.settings"

    override fun createPanel(): DialogPanel {
        return panel {
            group("Intentions") {
                row {
                    checkBox("Enable ‘Wrap with expected type’ intention")
                        .bindSelected(settings::enableWrapWithExpectedTypeIntention)
                }
                row {
                    checkBox("Enable ‘Wrap items with expected type’ intention")
                        .bindSelected(settings::enableWrapItemsWithExpectedTypeIntention)
                }
                row {
                    checkBox("Enable ‘Unwrap to expected type’ intention")
                        .bindSelected(settings::enableUnwrapToExpectedTypeIntention)
                }
                row {
                    checkBox("Enable ‘Unwrap items to expected type’ intention")
                        .bindSelected(settings::enableUnwrapItemsToExpectedTypeIntention)
                }
                row {
                    checkBox("Enable ‘Introduce custom type from stdlib’ intention")
                        .bindSelected(settings::enableIntroduceCustomTypeFromStdlibIntention)
                }
                row {
                    checkBox("Enable ‘Populate arguments’ intention")
                        .bindSelected(settings::enablePopulateArgumentsIntention)
                }
                row {
                    checkBox("Enable ‘Dictionary access’ intentions")
                        .bindSelected(settings::enableDictAccessIntention)
                }
                row {
                    checkBox("Enable ‘Change visibility’ intention")
                        .bindSelected(settings::enableChangeVisibilityIntention)
                }
                row {
                    checkBox("Enable ‘Introduce parameter object’ intention")
                        .bindSelected(settings::enableIntroduceParameterObjectIntention)
                }
                row {
                    checkBox("Enable ‘Create local variable’ intention")
                        .bindSelected(settings::enableCreateLocalVariableIntention)
                }
            }

            group("Inspections") {
                row {
                    checkBox("Enable ‘__all__’ export inspection")
                        .bindSelected(settings::enablePyMissingInDunderAllInspection)
                }
                row {
                    checkBox("Enable ‘Dataclass missing’ inspection")
                        .bindSelected(settings::enableDataclassMissingInspection)
                }
                row {
                    checkBox("Enable ‘Private module import’ inspection")
                        .bindSelected(settings::enablePrivateModuleImportInspection)
                }
            }

            group("Editor Actions") {
                row {
                    checkBox("Enable ‘Copy Package Content’ context menu action")
                        .bindSelected(settings::enableCopyPackageContentAction)
                }
                row {
                    checkBox("Enable ‘Copy Build Number’ context menu action")
                        .bindSelected(settings::enableCopyBuildNumberAction)
                }
            }

            group("Imports & Project View") {
                row {
                    checkBox("Enable ‘Restore Source Root Prefix’ in imports")
                        .bindSelected(settings::enableRestoreSourceRootPrefix)
                }
                row {
                    checkBox("Enable ‘Prefer relative imports’ in auto-import")
                        .bindSelected(settings::enableRelativeImportPreference)
                }
                row {
                    checkBox("Enable ‘Show Private Members’ filter in Structure View")
                        .bindSelected(settings::enableStructureViewPrivateMembersFilter)
                }
            }
        }
    }
}
