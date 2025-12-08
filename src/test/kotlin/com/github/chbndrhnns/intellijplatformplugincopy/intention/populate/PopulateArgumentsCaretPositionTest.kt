package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest
import fixtures.withPopulatePopupSelection

class PopulateArgumentsCaretPositionTest : TestBase() {

    fun testAvailableInsideParentheses() {
        withPopulatePopupSelection(index = 0) {
            myFixture.doIntentionTest(
                "a.py",
                """
                from dataclasses import dataclass
                @dataclass
                class A:
                    x: int

                A(<caret>)
                """,
                """
                from dataclasses import dataclass
                @dataclass
                class A:
                    x: int

                A(x=...)
                """,
                "Populate arguments..."
            )
        }
    }

    fun testNotAvailableOnClassName() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from dataclasses import dataclass
            @dataclass
            class A:
                x: int

            A<caret>()
            """,
            "Populate arguments..."
        )
    }

    fun testNotAvailableBeforeParentheses() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from dataclasses import dataclass
            @dataclass
            class A:
                x: int

            A<caret>()
            """,
            "Populate arguments..."
        )
    }

    fun testNotAvailableAfterParentheses() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from dataclasses import dataclass
            @dataclass
            class A:
                x: int

            A()<caret>
            """,
            "Populate arguments..."
        )
    }
}
