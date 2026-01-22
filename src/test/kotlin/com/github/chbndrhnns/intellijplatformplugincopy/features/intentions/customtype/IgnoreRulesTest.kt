package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionAvailable
import fixtures.assertRefactoringActionNotAvailable

class IgnoreRulesTest : TestBase() {

    private val actionId = "com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testIntentionNotOfferedInsideDunderAllValues() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            __all__ = ["i<caret>nt"]
            """,
            actionId
        )
    }

    fun testIntentionNotOfferedForIgnoredSymbolNames() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            __version__: int = 1<caret>23
            """,
            actionId
        )
    }

    fun testIntentionStillOfferedForRegularSymbol() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            def f():
                value: int = 1<caret>23
            """,
            actionId
        )
    }
}
