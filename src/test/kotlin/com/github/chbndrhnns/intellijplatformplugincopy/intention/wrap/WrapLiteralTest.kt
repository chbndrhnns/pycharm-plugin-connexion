package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase

class WrapLiteralTest : TestBase() {

    fun testLiteral_NoWrapOffered() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import Literal

            def foo(mode: Literal['r', 'w']):
                pass

            foo(<caret>'w')
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val wrapIntention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with Literal") }
        assertNull("Wrap intention with Literal should not be offered", wrapIntention)
    }

    fun testLiteral_Open_NoWrapOffered() {
        myFixture.configureByText(
            "a.py",
            """
            # open definition usually comes from builtins or io, but for simplicity let's mock a function with Literal
            from typing import Literal
            
            def my_open(file: str, mode: Literal['r', 'w', 'r+', 'a']):
                pass

            my_open('data.json', <caret>'x')
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val wrapIntention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with Literal") }
        assertNull("Wrap intention with Literal should not be offered", wrapIntention)
    }
}
