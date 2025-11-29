package fixtures

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState

object SettingsTestUtils {
    inline fun withPluginSettings(
        crossinline transform: (PluginSettingsState.State) -> PluginSettingsState.State,
        block: () -> Unit,
    ) {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(transform(old))
            block()
        } finally {
            svc.loadState(old)
        }
    }
}
