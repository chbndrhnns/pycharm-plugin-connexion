package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions

import com.intellij.openapi.command.WriteCommandAction
import fixtures.TestBase

class AddSelfParameterIntentionTest : TestBase() {
    fun testAddSelfParameterPreview() {
        myFixture.configureByText(
            "a.py", """
            class A:
                def foo(<caret>):
                    pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Add 'self' parameter")
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            val info = intention.generatePreview(myFixture.project, myFixture.editor, myFixture.file)
            assertFalse("Preview info should not be empty", info.toString().isEmpty())
        }
    }

    fun testNotAvailableForTopLevelFunction() {
        myFixture.configureByText(
            "a.py", """
            def foo(<caret>):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Add 'self' parameter")
        assertEmpty(intentions)
    }
}
