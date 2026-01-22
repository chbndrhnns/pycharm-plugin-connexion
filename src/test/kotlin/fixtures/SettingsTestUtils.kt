package fixtures

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.util.xmlb.XmlSerializerUtil

object SettingsTestUtils {

    fun withPluginSettings(
        configure: PluginSettingsState.State.() -> Unit,
        block: () -> Unit,
    ) {
        val svc = PluginSettingsState.instance()
        // Create a deep copy of the state to restore later.
        // XmlSerializerUtil is robust and handles @State classes automatically.
        val oldState = XmlSerializerUtil.createCopy(svc.state)
        try {
            // Mutate the current state directly
            svc.state.configure()
            block()
        } finally {
            svc.loadState(oldState)
        }
    }
}
