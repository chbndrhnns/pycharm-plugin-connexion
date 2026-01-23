package com.github.chbndrhnns.betterpy.features.intentions.suppressor

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.daemon.impl.IntentionActionFilter
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyFile

class RenameToSelfFilter : IntentionActionFilter, DumbAware {
    override fun accept(intentionAction: IntentionAction, file: PsiFile?): Boolean {
        if (!PluginSettingsState.instance().state.enableRenameToSelfFilter) return true
        if (file !is PyFile) return true

        val text = intentionAction.text
        if (text != "Rename to 'self'" && text != "Rename to self") {
            return true
        }

        return false
    }
}
