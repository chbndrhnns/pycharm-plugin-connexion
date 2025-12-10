package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel

class PluginSettingsConfigurable : BoundConfigurable("DDD Toolkit"), SearchableConfigurable {
    override fun getId(): String = "com.github.chbndrhnns.intellijplatformplugincopy.settings"

    override fun createPanel(): DialogPanel {
        return panel {
            row {
                label("Please select a category to configure settings.")
            }
        }
    }
}
