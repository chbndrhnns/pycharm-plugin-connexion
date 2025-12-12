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
            row {
                checkBox("‘Introduce Parameter Object…’ action (editor)")
                    .bindSelected(settings::enableIntroduceParameterObjectAction)
            }
            row {
                checkBox("‘Introduce Parameter Object…’ action (refactoring menu)")
                    .bindSelected(settings::enableIntroduceParameterObjectRefactoringAction)
            }
            row {
                checkBox("‘Copy with Dependencies’ action")
                    .bindSelected(settings::enableCopyBlockWithDependenciesAction)
            }
            row {
                checkBox("‘Copy Pytest Node IDs’ action (Test Tree)")
                    .bindSelected(settings::enableCopyPytestNodeIdsAction)
            }
            row {
                checkBox("‘Copy FQNs’ action (Test Tree)")
                    .bindSelected(settings::enableCopyFQNsAction)
            }
            row {
                checkBox("‘Copy Stacktrace’ action (Test Tree)")
                    .bindSelected(settings::enableCopyStacktraceAction)
            }
            row {
                checkBox("‘Jump to Test Tree Node’ action (editor)")
                    .bindSelected(settings::enableJumpToPytestNodeInTestTreeAction)
            }

            // Added: Editor/Contributor integrations
            row {
                checkBox("‘Return value’ completion contributor")
                    .bindSelected(settings::enablePyReturnCompletionContributor)
            }
            row {
                checkBox("‘mock.patch’ reference contributor")
                    .bindSelected(settings::enablePyMockPatchReferenceContributor)
            }
            row {
                checkBox("‘filterwarnings’ reference contributor")
                    .bindSelected(settings::enablePyFilterWarningsReferenceContributor)
            }
            row {
                checkBox("‘Type annotation’ usage filtering rule")
                    .bindSelected(settings::enableTypeAnnotationUsageFilteringRule)
            }
            row {
                checkBox("‘Python message’ console filter")
                    .bindSelected(settings::enablePyMessageConsoleFilter)
            }
            row {
                checkBox("‘Pytest identifiers’ Search Everywhere contributor")
                    .bindSelected(settings::enablePytestIdentifierSearchEverywhereContributor)
            }
        }
    }
}
