package com.github.chbndrhnns.intellijplatformplugincopy.intention.suppressor

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.daemon.impl.IntentionMenuContributor
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * Suppresses the platform-provided "Suggested Refactoring" change-signature intention
 * ("Update … to reflect signature change").
 */
class SuppressSuggestedRefactoringIntentionMenuContributor : IntentionMenuContributor {

    override fun collectActions(
        editor: Editor,
        file: PsiFile,
        intentions: ShowIntentionsPass.IntentionsInfo,
        passIdToShowIntentionsFor: Int,
        offset: Int,
    ) {
        if (!PluginSettingsState.instance().state.suppressSuggestedRefactoringSignatureChangeIntention) return

        filterList(intentions.intentionsToShow)
        filterList(intentions.errorFixesToShow)
        filterList(intentions.inspectionFixesToShow)
    }

    private fun filterList(list: MutableList<*>) {
        val iterator = list.listIterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            val action = extractIntentionAction(element) ?: continue
            if (isSuggestedRefactoringSignatureChange(action)) {
                iterator.remove()
            }
        }
    }

    private fun extractIntentionAction(descriptor: Any?): IntentionAction? {
        if (descriptor == null) return null

        // HighlightInfo.IntentionActionDescriptor is a platform class; we avoid a hard compile-time dependency
        // on its concrete type by accessing its `action` property reflectively.
        return try {
            val actionField = descriptor.javaClass.methods.firstOrNull { it.name == "getAction" && it.parameterCount == 0 }
                ?: descriptor.javaClass.fields.firstOrNull { it.name == "action" }
            when (actionField) {
                is java.lang.reflect.Method -> actionField.invoke(descriptor) as? IntentionAction
                is java.lang.reflect.Field -> actionField.get(descriptor) as? IntentionAction
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isSuggestedRefactoringSignatureChange(action: IntentionAction): Boolean {
        if (action.familyName != "Suggested Refactoring") return false
        val text = action.text
        return text.startsWith("Update ") && text.contains("reflect signature change", ignoreCase = true)
    }
}
