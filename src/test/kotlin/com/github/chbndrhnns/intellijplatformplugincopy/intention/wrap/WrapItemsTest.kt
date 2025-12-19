package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class WrapItemsTest : TestBase() {

    fun testList_CustomInt_WrapSingleElementAndWrapItems() {
        val text = """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                1,
                <caret>2,
                3,
            ]
            """
        myFixture.assertIntentionAvailable(
            "a.py",
            text,
            "BetterPy: Wrap with CustomInt()"
        )
        myFixture.assertIntentionAvailable(
            "a.py",
            text,
            "BetterPy: Wrap items with CustomInt()"
        )
    }

    fun testWrapSingleElement_InvokesSingleWrapping() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                1,
                <caret>2,
                3,
            ]
            """,
            """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                1,
                CustomInt(2),
                3,
            ]
            """,
            "BetterPy: Wrap with CustomInt()"
        )
    }

    fun testWrapItems_InvokesAllItemsWrapping() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                1,
                <caret>2,
                3,
            ]
            """,
            """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                CustomInt(1),
                CustomInt(2),
                CustomInt(3),
            ]
            """,
            "BetterPy: Wrap items with CustomInt()"
        )
    }

    fun testCustomInt_UnwrapAvailable_WrapNotOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[int] = [
            Custom<caret>Int(1),
            ] 
            """,
            "BetterPy: Wrap items with"
        )
    }


}
