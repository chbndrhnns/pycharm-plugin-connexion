package com.github.chbndrhnns.betterpy.featureflags

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger

/**
 * Persistent settings state for the plugin.
 * Only Connexion-related toggles are retained in this trimmed build.
 */
@State(name = "PycharmDddToolkitSettings", storages = [Storage("pycharm-ddd-toolkit.xml")])
class PluginSettingsState : PersistentStateComponent<PluginSettingsState.State> {
    data class State(
        var enableConnexionLineMarkers: Boolean = true,
        var enableConnexionInspections: Boolean = true,
        var enableConnexionCompletion: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        private val LOG = Logger.getInstance(PluginSettingsState::class.java)

        @JvmStatic
        fun instance(): PluginSettingsState {
            return serviceOrNull<PluginSettingsState>() ?: run {
                LOG.warn("PluginSettingsState service unavailable; using default state")
                PluginSettingsState()
            }
        }
    }
}
