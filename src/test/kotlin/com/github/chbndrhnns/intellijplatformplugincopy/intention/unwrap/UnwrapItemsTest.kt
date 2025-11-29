package com.github.chbndrhnns.intellijplatformplugincopy.intention.unwrap

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
        myFixture.assertIntentionAvailable("a.py", text, "Unwrap CustomInt()")
        myFixture.assertIntentionAvailable("a.py", text, "Unwrap items CustomInt()")
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
            "Unwrap items CustomInt()"
        )
    }
}
