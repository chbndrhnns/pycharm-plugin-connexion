package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase

class WrapSatisfiedUnionTest : TestBase() {

    fun testNoWrapIfAlreadySatisfied_StrInUnionListStr() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import List, Union

            def f(x: Union[List[str], str]):
                pass

            f(<caret>"abc")
            """.trimIndent()
        )
        val intention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with") }
        assertNull(
            "Should not offer wrap if argument already satisfies one union option. Found: ${intention?.text}",
            intention
        )
    }

    fun testNoWrapIfAlreadySatisfied_ListInUnionListStr() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import List, Union

            def f(x: Union[List[str], str]):
                pass

            f(<caret>["abc"])
            """.trimIndent()
        )
        val intention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with") }
        assertNull(
            "Should not offer wrap if argument already satisfies one union option. Found: ${intention?.text}",
            intention
        )
    }

    fun testNoWrapIfAlreadySatisfied_IntInUnionListInt() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import List, Union

            def f(x: Union[List[int], int]):
                pass

            f(<caret>1)
            """.trimIndent()
        )
        val intention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with") }
        assertNull(
            "Should not offer wrap if argument already satisfies one union option. Found: ${intention?.text}",
            intention
        )
    }

    fun testKnownWorkingCase() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path

            def f(p: int | Path) -> None:
                pass

            f(<caret>"val")
            """.trimIndent()
        )
        val intention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with") }
        assertNotNull("Known working case should have intention", intention)
    }

    fun testNoWrapIfAlreadySatisfied_ListInUnionListMyClass() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import List, Union

            class MyClass:
                pass

            def f(x: Union[List[MyClass], MyClass]):
                pass

            f(<caret>[MyClass()])
            """.trimIndent()
        )
        val intention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with") }
        assertNull(
            "Should not offer wrap if argument satisfies lower priority union option. Found: ${intention?.text}",
            intention
        )
    }
}
