package com.github.chbndrhnns.betterpy.features.intentions.suppressor

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import fixtures.TestBase

class SuppressSuggestedRefactoringIntentionActionFilterTest : TestBase() {

    fun testRemovesSuggestedRefactoringSignatureChangeWhenSuppressed() {
        PluginSettingsState.instance().state.suppressSuggestedRefactoringSignatureChangeIntention = true

        val action = FakeIntentionAction(
            familyName = "Suggested Refactoring",
            text = "Update usages to reflect signature change",
        )

        val filter = SuppressSuggestedRefactoringIntentionActionFilter()
        assertFalse(filter.accept(action, null))
    }

    fun testKeepsSuggestedRefactoringSignatureChangeWhenNotSuppressed() {
        PluginSettingsState.instance().state.suppressSuggestedRefactoringSignatureChangeIntention = false

        val action = FakeIntentionAction(
            familyName = "Suggested Refactoring",
            text = "Update usages to reflect signature change",
        )

        val filter = SuppressSuggestedRefactoringIntentionActionFilter()
        assertTrue(filter.accept(action, null))
    }

    fun testKeepsOtherIntentions() {
        PluginSettingsState.instance().state.suppressSuggestedRefactoringSignatureChangeIntention = true

        val action = FakeIntentionAction(
            familyName = "Some Other Family",
            text = "Some other intention",
        )

        val filter = SuppressSuggestedRefactoringIntentionActionFilter()
        assertTrue(filter.accept(action, null))
    }

    private class ActionDescriptor(private val action: IntentionAction) {
        fun getAction(): IntentionAction = action
    }

    private class FakeIntentionAction(
        private val familyName: String,
        private val text: String,
    ) : IntentionAction {
        override fun getText(): String = text
        override fun getFamilyName(): String = familyName
        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true
        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) = Unit
        override fun startInWriteAction(): Boolean = false
    }
}
