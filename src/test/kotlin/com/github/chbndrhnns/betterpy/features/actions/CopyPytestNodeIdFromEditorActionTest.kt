package com.github.chbndrhnns.betterpy.features.actions

import com.intellij.openapi.ide.CopyPasteManager
import fixtures.TestBase
import java.awt.datatransfer.DataFlavor

class CopyPytestNodeIdFromEditorActionTest : TestBase() {

    fun testCopyPytestNodeId() {
        myFixture.configureByText(
            "test_something.py",
            """
            def test_foo<caret>():
                pass
            """.trimIndent()
        )

        myFixture.performEditorAction("MyPlugin.CopyPytestNodeIdFromEditorAction")

        val clipboardContent = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) as? String
        assertNotNull(clipboardContent)
        assertEquals("test_something.py::test_foo", clipboardContent)
    }

    fun testActionUnavailableInNonTestFile() {
        myFixture.configureByText(
            "main.py",
            """
            def test_foo<caret>():
                pass
            """.trimIndent()
        )

        val presentation =
            myFixture.testAction(com.github.chbndrhnns.betterpy.features.actions.CopyPytestNodeIdFromEditorAction())
        assertFalse("Action should be disabled in non-test file", presentation.isEnabledAndVisible)
    }

    fun testActionUnavailableOutsideTestFunction() {
        myFixture.configureByText(
            "test_something.py",
            """
            def helper<caret>():
                pass
            """.trimIndent()
        )

        val presentation =
            myFixture.testAction(com.github.chbndrhnns.betterpy.features.actions.CopyPytestNodeIdFromEditorAction())
        assertFalse("Action should be disabled in non-test function", presentation.isEnabledAndVisible)
    }
}
