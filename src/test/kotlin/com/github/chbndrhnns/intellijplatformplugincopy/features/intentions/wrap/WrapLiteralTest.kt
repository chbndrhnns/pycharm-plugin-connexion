package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class WrapLiteralTest : TestBase() {

    fun testLiteral_NoWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import Literal

            def foo(mode: Literal['r', 'w']):
                pass

            foo(<caret>'w')
            """,
            "BetterPy: Wrap with Literal"
        )
    }

    fun testLiteral_Open_NoWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            # open definition usually comes from builtins or io, but for simplicity let's mock a function with Literal
            from typing import Literal
            
            def my_open(file: str, mode: Literal['r', 'w', 'r+', 'a']):
                pass

            my_open('data.json', <caret>'x')
            """,
            "BetterPy: Wrap with Literal"
        )
    }
}
