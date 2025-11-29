package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.assertIntentionNotAvailable

class IgnoreRulesTest : TestBase() {

    fun testIntentionNotOfferedInsideDunderAllValues() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            __all__ = ["i<caret>nt"]
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionNotOfferedForIgnoredSymbolNames() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            __version__: int = 1<caret>23
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionStillOfferedForRegularSymbol() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            def f():
                value: int = 1<caret>23
            """,
            "Introduce custom type"
        )
    }
}
