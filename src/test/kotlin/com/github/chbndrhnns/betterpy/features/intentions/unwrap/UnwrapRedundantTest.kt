package com.github.chbndrhnns.betterpy.features.intentions.unwrap

import fixtures.TestBase
import fixtures.doIntentionTest

class UnwrapRedundantTest : TestBase() {

    fun testRedundantIntCast_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def test_():
                val: int = 1
                return int(va<caret>l)
            """.trimIndent(),
            """
            def test_():
                val: int = 1
                return val
            """.trimIndent(),
            "BetterPy: Unwrap int()"
        )
    }

    fun testRedundantIntCast_OnCall_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def test_():
                val: int = 1
                return in<caret>t(val)
            """.trimIndent(),
            """
            def test_():
                val: int = 1
                return val
            """.trimIndent(),
            "BetterPy: Unwrap int()"
        )
    }
}
