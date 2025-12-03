package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "PycharmDddToolkitSettings", storages = [Storage("pycharm-ddd-toolkit.xml")])
class PluginSettingsState : PersistentStateComponent<PluginSettingsState.State> {
    data class State(
        var enableWrapWithExpectedTypeIntention: Boolean = true,
        var enableWrapItemsWithExpectedTypeIntention: Boolean = true,
        var enableUnwrapToExpectedTypeIntention: Boolean = true,
        var enableUnwrapItemsToExpectedTypeIntention: Boolean = true,
        var enableIntroduceCustomTypeFromStdlibIntention: Boolean = true,
        var enablePopulateArgumentsIntention: Boolean = true,
        var enablePyMissingInDunderAllInspection: Boolean = true,
        var enableCopyPackageContentAction: Boolean = true,
        var enableRestoreSourceRootPrefix: Boolean = true,
        var enableRelativeImportPreference: Boolean = true,
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        @JvmStatic
        fun instance(): PluginSettingsState = service()
    }
}
