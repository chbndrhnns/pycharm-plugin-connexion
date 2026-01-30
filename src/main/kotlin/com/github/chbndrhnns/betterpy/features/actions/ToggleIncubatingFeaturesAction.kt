package com.github.chbndrhnns.betterpy.features.actions

import com.github.chbndrhnns.betterpy.featureflags.FeatureRegistry
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.StatusBar

class ToggleIncubatingFeaturesAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val settings = PluginSettingsState.instance()
        settings.toggleIncubatingFeatures()
        val message = if (settings.isIncubatingOverrideActive()) {
            "Incubating features toggled (temporary until restart)"
        } else {
            "Incubating features restored"
        }
        StatusBar.Info.set(message, e.project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val hasIncubating = FeatureRegistry.instance().getIncubatingFeatures().isNotEmpty()
        e.presentation.isEnabledAndVisible = hasIncubating
    }
}
