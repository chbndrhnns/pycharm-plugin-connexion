package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PluginSettingsConfigurable : SearchableConfigurable {
    private var panel: JPanel? = null
    private lateinit var enableWrapCb: JBCheckBox

    override fun getId(): String = "com.github.chbndrhnns.intellijplatformplugincopy.settings"
    override fun getDisplayName(): String = "DDD Toolkit"

    override fun createComponent(): JComponent {
        val p = JPanel(BorderLayout())
        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        enableWrapCb = JBCheckBox("Enable ‘Wrap with expected type’ intention")
        inner.add(enableWrapCb)
        p.add(inner, BorderLayout.NORTH)
        panel = p
        return p
    }

    override fun isModified(): Boolean {
        val state = PluginSettingsState.instance().state
        return ::enableWrapCb.isInitialized && enableWrapCb.isSelected != state.enableWrapIntention
    }

    override fun apply() {
        val svc = PluginSettingsState.instance()
        val s = svc.state.copy(enableWrapIntention = enableWrapCb.isSelected)
        svc.loadState(s)
    }

    override fun reset() {
        val st = PluginSettingsState.instance().state
        if (::enableWrapCb.isInitialized) enableWrapCb.isSelected = st.enableWrapIntention
    }

    override fun disposeUIResources() {
        panel = null
    }
}
