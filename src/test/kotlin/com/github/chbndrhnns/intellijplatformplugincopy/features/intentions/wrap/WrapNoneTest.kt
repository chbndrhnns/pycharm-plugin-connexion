package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class WrapNoneTest : TestBase() {

    fun testNone_IntentionNotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class Wrapper:
                def __init__(self, x):
                    pass
            
            def func(w: Wrapper):
                pass
            
            func(<caret>None)
            """,
            "BetterPy: Wrap with Wrapper()"
        )
    }

    fun testNoneInList_IntentionNotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class Wrapper:
                def __init__(self, x):
                    pass
            
            def func(w: list[Wrapper]):
                pass
            
            func([<caret>None])
            """,
            "BetterPy: Wrap items with Wrapper()"
        )
    }
}
