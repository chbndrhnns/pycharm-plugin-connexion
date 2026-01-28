package com.github.chbndrhnns.betterpy.features.intentions.populate

import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.assertIntentionNotAvailable

class PopulateRecursiveFromLocalsIntentionTest : TestBase() {

    fun testAvailable_NoUnion() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            from dataclasses import dataclass

            @dataclass
            class B:
                x: int

            @dataclass
            class A:
                b: B

            a = A(<caret>)
            """,
            "BetterPy: Populate arguments recursively"
        )
    }

    fun testNotAvailable_WhenUnionPresent() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from dataclasses import dataclass

            @dataclass
            class A:
                v: int | str

            a = A(<caret>)
            """,
            "BetterPy: Populate arguments recursively"
        )
    }

    fun testPopulate_RecursiveWithLocalsFastPath() {
        myFixture.configureByText(
            "a.py",
            """
            from dataclasses import dataclass

            @dataclass
            class B:
                x: int

            @dataclass
            class A:
                b: B
                name: str

            b = B(x=1)
            name = "hi"
            a = A(<caret>)
            """
                .trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Populate arguments recursively")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from dataclasses import dataclass

            @dataclass
            class B:
                x: int

            @dataclass
            class A:
                b: B
                name: str

            b = B(x=1)
            name = "hi"
            a = A(b=b, name=name)
            
            """.trimIndent()
        )
    }
}
