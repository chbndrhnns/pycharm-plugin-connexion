package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class WrapAvailabilityTest : TestBase() {
    fun testUnion_ValueMatchesOneType_NoWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            val: int | str | None
            val = <caret>2
            """,
            "Wrap with"
        )
    }


    fun testAssignmentTarget_IntentionNotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def func():
                va<caret>l: dict[str, int] = dict(a=1, b=2, c=3)
            """,
            "Wrap items with"
        )
    }


    fun testBuiltinFunctionName_IntentionNotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            print("a<caret>bc")
            """,
            "Wrap with"
        )
    }

    fun testDictConstructor_IntentionNotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class CustomInt(int):
                pass


            val: dict[str, CustomInt] = di<caret>ct({"a": 1, "b": 2, "c": 3})
            """,
            "Wrap items with CustomInt()"
        )
    }
}
