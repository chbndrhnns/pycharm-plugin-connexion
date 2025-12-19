package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase

class WrapContainerNestedTest : TestBase() {

    fun testWrapWithOuterNotOfferedOnNestedElement() {
        myFixture.configureByText(
            "a.py", """
            import dataclasses

            @dataclasses.dataclass
            class Inner:
                id: int

            @dataclasses.dataclass
            class Outer:
                inner: Inner

            val2: list[Outer] = [
                Outer(
                    inner=Inner(id=<caret>1),
                )
            ]
        """.trimIndent()
        )

        val intentions = myFixture.availableIntentions.map { it.text }
        assertTrue(
            "Intention 'BetterPy: Wrap with Outer()' should NOT be offered on deeply nested element, but it was. Available: $intentions",
            "BetterPy: Wrap with Outer()" !in intentions
        )
    }
}
