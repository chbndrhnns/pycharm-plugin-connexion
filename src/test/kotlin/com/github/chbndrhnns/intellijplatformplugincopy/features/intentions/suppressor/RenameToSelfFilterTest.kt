package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.suppressor

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import fixtures.TestBase

class RenameToSelfFilterTest : TestBase() {

    fun testFiltersRenameToSelfWhenEnabled() {
        PluginSettingsState.instance().state.enableRenameToSelfFilter = true
        val filter = RenameToSelfFilter()

        myFixture.configureByText(
            "a.py", """
            class A:
                def foo(bar):
                    pass
        """.trimIndent()
        )
        val action = FakeIntentionAction("Rename to 'self'")

        assertFalse(filter.accept(action, myFixture.file))
    }

    fun testDoesNotFilterRenameToSelfWhenDisabled() {
        PluginSettingsState.instance().state.enableRenameToSelfFilter = false
        val filter = RenameToSelfFilter()

        myFixture.configureByText(
            "a.py", """
            class A:
                def foo(bar):
                    pass
        """.trimIndent()
        )
        val action = FakeIntentionAction("Rename to 'self'")

        assertTrue(filter.accept(action, myFixture.file))
    }

    fun testDoesNotFilterOtherIntentions() {
        PluginSettingsState.instance().state.enableRenameToSelfFilter = true
        val filter = RenameToSelfFilter()

        myFixture.configureByText(
            "a.py", """
            class A:
                def foo(bar):
                    pass
        """.trimIndent()
        )
        val action = FakeIntentionAction("Some other action")

        assertTrue(filter.accept(action, myFixture.file))
    }

    fun testDoesNotFilterInNonPyFiles() {
        PluginSettingsState.instance().state.enableRenameToSelfFilter = true
        val filter = RenameToSelfFilter()

        myFixture.configureByText("a.txt", "Rename to 'self'")
        val action = FakeIntentionAction("Rename to 'self'")

        assertTrue(filter.accept(action, myFixture.file))
    }

    private class FakeIntentionAction(private val text: String) : IntentionAction {
        override fun getText(): String = text
        override fun getFamilyName(): String = "Rename"
        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true
        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) = Unit
        override fun startInWriteAction(): Boolean = false
    }
}
