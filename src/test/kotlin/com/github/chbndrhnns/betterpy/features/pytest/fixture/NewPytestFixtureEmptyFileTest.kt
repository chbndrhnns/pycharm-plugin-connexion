package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import fixtures.TestBase

class NewPytestFixtureEmptyFileTest : TestBase() {

    fun testNewPytestFixtureInEmptyFile() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        // Configure an empty test file
        myFixture.configureByText(
            "test_empty.py",
            "<caret>"
        )

        val action = NewPytestFixtureAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())

        // Ensure action is enabled
        action.update(event)
        assertTrue(event.presentation.isEnabledAndVisible)

        action.actionPerformed(event)

        // Expectation: import at top, followed by fixture
        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def new_fixture():
                pass
            """.trimIndent()
        )
    }

    private fun editorDataContext() = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, myFixture.editor)
        .add(CommonDataKeys.PSI_FILE, myFixture.file)
        .build()
}
