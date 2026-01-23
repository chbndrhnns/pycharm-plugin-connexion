package com.github.chbndrhnns.betterpy.features.intentions.suppressor

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.daemon.impl.IntentionActionFilter
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile

class SuppressSuggestedRefactoringIntentionActionFilter : IntentionActionFilter, DumbAware {

    override fun accept(action: IntentionAction, file: PsiFile?): Boolean {
        if (!PluginSettingsState.instance().state.suppressSuggestedRefactoringSignatureChangeIntention) {
            return true
        }

        if (isSuggestedRefactoringSignatureChange(action)) {
            return false
        }

        return true
    }

    private fun isSuggestedRefactoringSignatureChange(action: IntentionAction): Boolean {
        if (action.familyName != "Suggested Refactoring") return false
        val text = action.text
        return text.startsWith("Update ") && text.contains("reflect signature change", ignoreCase = true)
    }
}
