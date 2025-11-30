package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class WrapSelfTest : TestBase() {
    fun testDoNotWrapWithSelf() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class C:
                def do(self, val: str):
                    ...

                def other(self):
                    self.do("ab<caret>c")
            """,
            "Wrap with"
        )
    }
}
