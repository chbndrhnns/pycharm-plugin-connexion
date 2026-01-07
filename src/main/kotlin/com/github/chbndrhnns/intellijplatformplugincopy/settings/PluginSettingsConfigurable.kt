package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel

class PluginSettingsConfigurable : BoundConfigurable("BetterPy"), SearchableConfigurable {
    override fun getId(): String = "com.github.chbndrhnns.intellijplatformplugincopy.settings"

    override fun createPanel(): DialogPanel {
        return createFilterableFeaturePanel { _, _, _ ->
            row {
                label("Please select a category to configure settings.")
            }
        }.asDialogPanel()
    }
}
