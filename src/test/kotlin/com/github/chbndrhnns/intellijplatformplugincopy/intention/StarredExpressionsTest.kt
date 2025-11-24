package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class StarredExpressionsTest : TestBase() {

    fun testWrapWithExpectedType_UnavailableOnStarredExpression() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: int): pass
            l = ["s"]
            f(*<caret>l)
            """.trimIndent()
        )
        assertIntentionNotAvailable("Wrap with expected type")
    }

    fun testWrapWithExpectedType_UnavailableOnDoubleStarredExpression() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: int): pass
            d = {"x": "s"}
            f(**<caret>d)
            """.trimIndent()
        )
        assertIntentionNotAvailable("Wrap with expected type")
    }

    fun testUnwrapToExpectedType_UnavailableOnStarredExpression() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            def f(x: int): pass
            val = UserId(1)
            f(*<caret>val) 
            """.trimIndent()
        )
        assertIntentionNotAvailable("Unwrap UserId()")
    }

    fun testWrapWithExpectedType_UnavailableOnStarredNonIterable() {
        myFixture.configureByText(
            "a.py",
            """
            l = [*<caret>1]
            """.trimIndent()
        )
        // Expected: Iterable (mapped to list). Actual: int.
        // Intention should NOT be available according to issue.
        // If it IS available, this test will fail, confirming reproduction.
        assertIntentionNotAvailable("Wrap with expected type")
    }

    fun testIntroduceCustomType_UnavailableOnStarredExpression() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x): pass
            val = [1]
            f(*<caret>list(val))
            """.trimIndent()
        )
        assertIntentionNotAvailable("Introduce custom type from list")
    }

    fun testWrapItems_UnavailableOnStarredExpression() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: int): pass
            f(*[<caret>"s"])
            """.trimIndent()
        )
        assertIntentionNotAvailable("Wrap items")
    }

    private fun assertIntentionNotAvailable(namePrefix: String) {
        val available = myFixture.availableIntentions.any { it.text.startsWith(namePrefix) }
        assertFalse("Intention '$namePrefix' should not be available", available)
    }
}
