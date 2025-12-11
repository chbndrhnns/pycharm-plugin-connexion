package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class IntentionsConfigurable : BoundConfigurable("Intentions"), Configurable.WithEpDependencies {
    private val settings = PluginSettingsState.instance().state

    override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
        return listOf(ExtensionPointName.create<Any>("com.intellij.intentionAction"))
    }

    override fun createPanel(): DialogPanel {
        return panel {
            row {
                checkBox("‘Wrap with expected type’ intention")
                    .bindSelected(settings::enableWrapWithExpectedTypeIntention)
            }
            row {
                checkBox("‘Wrap items with expected type’ intention")
                    .bindSelected(settings::enableWrapItemsWithExpectedTypeIntention)
            }
            row {
                checkBox("‘Unwrap to expected type’ intention")
                    .bindSelected(settings::enableUnwrapToExpectedTypeIntention)
            }
            row {
                checkBox("‘Unwrap items to expected type’ intention")
                    .bindSelected(settings::enableUnwrapItemsToExpectedTypeIntention)
            }
            row {
                checkBox("‘Introduce custom type from stdlib’ intention")
                    .bindSelected(settings::enableIntroduceCustomTypeFromStdlibIntention)
            }
            row {
                checkBox("‘Populate arguments’ intention")
                    .bindSelected(settings::enablePopulateArgumentsIntention)
            }
            row {
                checkBox("‘Dictionary access’ intentions")
                    .bindSelected(settings::enableDictAccessIntention)
            }
            row {
                checkBox("‘Change visibility’ intention")
                    .bindSelected(settings::enableChangeVisibilityIntention)
            }
            row {
                checkBox("‘Introduce parameter object’ intention")
                    .bindSelected(settings::enableIntroduceParameterObjectIntention)
            }
            row {
                checkBox("‘Create local variable’ intention")
                    .bindSelected(settings::enableCreateLocalVariableIntention)
            }
            row {
                checkBox("‘Make parameter optional’ intention")
                    .bindSelected(settings::enableMakeParameterOptionalIntention)
            }
            row {
                checkBox("‘Make parameter mandatory’ intention")
                    .bindSelected(settings::enableMakeParameterMandatoryIntention)
            }
            row {
                checkBox("‘Add exception capture’ intention")
                    .bindSelected(settings::enableAddExceptionCaptureIntention)
            }
            row {
                checkBox("‘Wrap exceptions with parentheses’ intention")
                    .bindSelected(settings::enableWrapExceptionsWithParenthesesIntention)
            }
            row {
                checkBox("‘Implement abstract method in child classes’ intention")
                    .bindSelected(settings::enableImplementAbstractMethodInChildClassesIntention)
            }
            row {
                checkBox("‘Toggle pytest skip’ intention")
                    .bindSelected(settings::enableTogglePytestSkipIntention)
            }
            row {
                checkBox("‘Parametrize pytest test’ intention")
                    .bindSelected(settings::enableParametrizePytestTestIntention)
            }
        }
    }
}
