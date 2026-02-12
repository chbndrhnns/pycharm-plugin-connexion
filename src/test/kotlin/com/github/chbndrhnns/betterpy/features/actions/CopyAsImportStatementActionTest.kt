package com.github.chbndrhnns.betterpy.features.actions

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.ide.CopyPasteManager
import fixtures.TestBase
import java.awt.datatransfer.DataFlavor

class CopyAsImportStatementActionTest : TestBase() {

    fun testCopyTopLevelFunction() {
        myFixture.configureByText(
            "my_module.py",
            """
            def my_func<caret>():
                pass
            """.trimIndent()
        )

        myFixture.performEditorAction("MyPlugin.CopyAsImportStatementAction")

        val clipboardContent = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) as? String
        assertNotNull(clipboardContent)
        assertEquals("from my_module import my_func", clipboardContent)
    }

    fun testCopyTopLevelClass() {
        myFixture.configureByText(
            "my_module.py",
            """
            class My<caret>Class:
                pass
            """.trimIndent()
        )

        myFixture.performEditorAction("MyPlugin.CopyAsImportStatementAction")

        val clipboardContent = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) as? String
        assertNotNull(clipboardContent)
        assertEquals("from my_module import MyClass", clipboardContent)
    }

    fun testActionUnavailableOnNestedFunction() {
        myFixture.configureByText(
            "my_module.py",
            """
            def outer():
                def inner<caret>():
                    pass
            """.trimIndent()
        )

        val presentation = myFixture.testAction(CopyAsImportStatementAction())
        assertFalse("Action should be disabled on nested function", presentation.isEnabledAndVisible)
    }

    fun testActionUnavailableOnNestedClass() {
        myFixture.configureByText(
            "my_module.py",
            """
            class Outer:
                class Inner<caret>:
                    pass
            """.trimIndent()
        )

        val presentation = myFixture.testAction(CopyAsImportStatementAction())
        assertFalse("Action should be disabled on nested class", presentation.isEnabledAndVisible)
    }

    fun testActionUnavailableWhenDisabled() {
        PluginSettingsState.instance().state.enableCopyAsImportStatementAction = false

        myFixture.configureByText(
            "my_module.py",
            """
            def my_func<caret>():
                pass
            """.trimIndent()
        )

        val presentation = myFixture.testAction(CopyAsImportStatementAction())
        assertFalse("Action should be disabled when setting is off", presentation.isEnabledAndVisible)
    }

    fun testActionUnavailableOnNonPythonFile() {
        myFixture.configureByText(
            "readme.txt",
            """
            some text<caret> here
            """.trimIndent()
        )

        val presentation = myFixture.testAction(CopyAsImportStatementAction())
        assertFalse("Action should be disabled on non-Python files", presentation.isEnabledAndVisible)
    }
}
