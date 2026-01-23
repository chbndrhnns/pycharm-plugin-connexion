package com.github.chbndrhnns.betterpy.features.intentions.unwrap

import fixtures.TestBase

import fixtures.assertIntentionAvailable
import fixtures.doIntentionTest

class UnwrapItemsTest : TestBase() {

    fun testList_CustomInt_UnwrapItems() {
        val text = """
            class CustomInt(int):
                pass

            val: list[int] = [
                CustomInt(1),
                <caret>CustomInt(2),
                CustomInt(3),
            ]
            """
        myFixture.assertIntentionAvailable("a.py", text, "BetterPy: Unwrap CustomInt()")
        myFixture.assertIntentionAvailable("a.py", text, "BetterPy: Unwrap items CustomInt()")
    }

    fun testUnwrapItems_InvokesAllItemsUnwrapping() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[int] = [
                CustomInt(1),
                <caret>CustomInt(2),
                CustomInt(3),
            ]
            """,
            """
            class CustomInt(int):
                pass

            val: list[int] = [
                1,
                2,
                3,
            ]
            """,
            "BetterPy: Unwrap items CustomInt()"
        )
    }
}
