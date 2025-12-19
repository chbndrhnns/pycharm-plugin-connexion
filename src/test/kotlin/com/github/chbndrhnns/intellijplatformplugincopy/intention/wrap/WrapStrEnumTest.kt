package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.doIntentionTest

class WrapStrEnumTest : TestBase() {

    fun testStrEnumMember_IntLiteral_WrapsWithStr() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from enum import StrEnum

            class Variant(StrEnum):
                ONE = <caret>1
            """,
            """
            from enum import StrEnum

            class Variant(StrEnum):
                ONE = str(1)
            """,
            "BetterPy: Wrap with str"
        )
    }
}
