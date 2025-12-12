package com.github.chbndrhnns.intellijplatformplugincopy.intention.suppressor

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import fixtures.TestBase

class SuppressSuggestedRefactoringIntentionMenuContributorTest : TestBase() {

    fun testRemovesSuggestedRefactoringSignatureChangeWhenSuppressed() {
        PluginSettingsState.instance().state.suppressSuggestedRefactoringSignatureChangeIntention = true

        myFixture.configureByText("a.py", """def f(a):\n    return a\n""")

        val intentions = ShowIntentionsPass.IntentionsInfo()
        val descriptor = ActionDescriptor(
            action = FakeIntentionAction(
                familyName = "Suggested Refactoring",
                text = "Update usages to reflect signature change",
            ),
        )
        (intentions.intentionsToShow as MutableList<Any>).add(descriptor)

        SuppressSuggestedRefactoringIntentionMenuContributor().collectActions(
            editor = myFixture.editor,
            file = myFixture.file,
            intentions = intentions,
            passIdToShowIntentionsFor = 0,
            offset = myFixture.caretOffset,
        )

        assertTrue(intentions.intentionsToShow.isEmpty())
    }

    fun testKeepsSuggestedRefactoringSignatureChangeWhenNotSuppressed() {
        PluginSettingsState.instance().state.suppressSuggestedRefactoringSignatureChangeIntention = false

        myFixture.configureByText("a.py", """def f(a):\n    return a\n""")

        val intentions = ShowIntentionsPass.IntentionsInfo()
        val descriptor = ActionDescriptor(
            action = FakeIntentionAction(
                familyName = "Suggested Refactoring",
                text = "Update usages to reflect signature change",
            ),
        )
        (intentions.intentionsToShow as MutableList<Any>).add(descriptor)

        SuppressSuggestedRefactoringIntentionMenuContributor().collectActions(
            editor = myFixture.editor,
            file = myFixture.file,
            intentions = intentions,
            passIdToShowIntentionsFor = 0,
            offset = myFixture.caretOffset,
        )

        assertEquals(1, intentions.intentionsToShow.size)
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
