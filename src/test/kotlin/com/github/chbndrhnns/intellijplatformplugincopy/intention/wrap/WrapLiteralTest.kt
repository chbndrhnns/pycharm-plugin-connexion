package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

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
        // It might suggest "Wrap with Literal['r']" or something similar if the bug is present.
        // We want to ensure no "Wrap with Literal..." intention is present.
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

    fun testLiteral_CustomClassNamedLiteral_NoWrapOffered() {
        myFixture.configureByText(
            "a.py",
            """
            class Literal:
                def __init__(self, val):
                    pass

            def foo(mode: Literal):
                pass

            foo(<caret>'w')
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val wrapIntention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with Literal") }
        assertNull("Wrap intention with Literal should not be offered even if it is a class", wrapIntention)
    }
}
