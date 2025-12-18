package com.github.chbndrhnns.intellijplatformplugincopy.intention

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

        val intention = myFixture.findSingleIntention("Add 'self' parameter")
        val info = intention.generatePreview(myFixture.project, myFixture.editor, myFixture.file)
        assertTextEquals("Contains preview", "", info.toString())
    }

    fun testNotAvailableForTopLevelFunction() {
        myFixture.configureByText(
            "a.py", """
            def foo(<caret>):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Add 'self' parameter")
        assertEmpty(intentions)
    }
}
