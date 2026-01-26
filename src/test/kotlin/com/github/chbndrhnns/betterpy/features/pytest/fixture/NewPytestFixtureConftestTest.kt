package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import fixtures.TestBase

class NewPytestFixtureConftestTest : TestBase() {

    fun testNewPytestFixtureActionVisibleInConftest() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "conftest.py",
            """
            <caret>
            """.trimIndent()
        )

        val action = NewPytestFixtureAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.update(event)

        assertTrue("Action should be visible in conftest.py", event.presentation.isEnabledAndVisible)
    }

    private fun editorDataContext() = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, myFixture.editor)
        .add(CommonDataKeys.PSI_FILE, myFixture.file)
        .build()
}
