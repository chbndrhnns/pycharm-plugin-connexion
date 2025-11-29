package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class StarredExpressionsTest : TestBase() {

    fun testWrapWithExpectedType_UnavailableOnStarredExpression() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(x: int): pass
            l = ["s"]
            f(*<caret>l)
            """,
            "Wrap with expected type"
        )
    }

    fun testWrapWithExpectedType_UnavailableOnDoubleStarredExpression() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(x: int): pass
            d = {"x": "s"}
            f(**<caret>d)
            """,
            "Wrap with expected type"
        )
    }

    fun testUnwrapToExpectedType_UnavailableOnStarredExpression() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            def f(x: int): pass
            val = UserId(1)
            f(*<caret>val) 
            """,
            "Unwrap UserId()"
        )
    }

    fun testWrapWithExpectedType_UnavailableOnStarredNonIterable() {
        // Expected: Iterable (mapped to list). Actual: int.
        // Intention should NOT be available according to issue.
        // If it IS available, this test will fail, confirming reproduction.
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            l = [*<caret>1]
            """,
            "Wrap with expected type"
        )
    }

    fun testIntroduceCustomType_UnavailableOnStarredExpression() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(x): pass
            val = [1]
            f(*<caret>list(val))
            """,
            "Introduce custom type from list"
        )
    }

    fun testWrapItems_UnavailableOnStarredExpression() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(x: int): pass
            f(*[<caret>"s"])
            """,
            "Wrap items"
        )
    }
}