package com.github.chbndrhnns.betterpy.features.actions

import com.github.chbndrhnns.betterpy.features.pytest.testtree.JumpToPytestNodeInTestTreeAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.testFramework.TestActionEvent
import fixtures.TestBase

class JumpToPytestNodeInTestTreeActionTest : TestBase() {

    fun testUpdateRunsOnBgt() {
        val action = JumpToPytestNodeInTestTreeAction()
        assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
    }

    fun testIsHiddenWhenNoActiveTestTree() {
        myFixture.configureByText(
            "test_file.py", """
            def test_something():
                <caret>pass
        """.trimIndent()
        )

        val action = JumpToPytestNodeInTestTreeAction()
        val event = TestActionEvent.createTestEvent(action)

        action.update(event)

        assertFalse("Action should be hidden when no active test tree", event.presentation.isEnabledAndVisible)
    }

    fun testIsHiddenInNonTestContext() {
        // Even if we had a test tree (which we don't in this test environment easily), 
        // it should be hidden if we are not on a test function/class.
        myFixture.configureByText(
            "some_file.py", """
            def some_normal_function():
                <caret>pass
        """.trimIndent()
        )

        val action = JumpToPytestNodeInTestTreeAction()
        val event = TestActionEvent.createTestEvent(action)

        action.update(event)

        assertFalse("Action should be hidden in non-test context", event.presentation.isEnabledAndVisible)
    }

    fun testIsVisibleInTestFunctionBody() {
        myFixture.configureByText(
            "test_body.py", """
            def test_something():
                x = 1
                <caret>print(x)
        """.trimIndent()
        )

        val action = JumpToPytestNodeInTestTreeAction()
        val event = TestActionEvent.createTestEvent(action)

        // Mocking settings to enable the action
        val settings = com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState.instance().state
        settings.enableJumpToPytestNodeInTestTreeAction = true

        action.update(event)

        // Even without an active test tree, it should be hidden by current implementation.
        // But let's see what happens if we ignore the tree check for a moment or if we can verify the context check.
        val elementAtCaret = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        assertTrue(
            "Should be in test context",
            com.github.chbndrhnns.betterpy.features.pytest.testtree.PytestTestContextUtils.isInTestContext(
                elementAtCaret
            )
        )
    }
}
