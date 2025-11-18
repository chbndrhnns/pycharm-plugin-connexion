package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.FakePopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.TestBase
import com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapWithExpectedTypeIntentionHooks

/**
 * Wrap intention behavior for forward-referenced types.
 */
class WrapForwardRefsTest : TestBase() {

    fun testNonContainerForwardRef_param() {
        myFixture.configureByText(
            "a.py",
            """
            from __future__ import annotations

            def f(x: 'A') -> None:
                ...

            class A:
                def __init__(self, value): ...

            f(<caret>value)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with A()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from __future__ import annotations

            def f(x: 'A') -> None:
                ...

            class A:
                def __init__(self, value): ...

            f(A(value))
            """.trimIndent()
        )
    }

    fun testUnionWithForwardRef_mixed() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from __future__ import annotations

                class B:
                    def __init__(self, v): ...

                def f(x: 'A' | B): ...

                class A:
                    def __init__(self, v): ...

                f(<caret>v)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            // We should offer both A and B; order is not strictly important, but A first is fine
            assertTrue(fake.lastLabels.any { it.startsWith("A") })
            assertTrue(fake.lastLabels.any { it.startsWith("B") })

            // First gets applied (A)
            myFixture.checkResult(
                """
                from __future__ import annotations

                class B:
                    def __init__(self, v): ...

                def f(x: 'A' | B): ...

                class A:
                    def __init__(self, v): ...

                f(A(v))
                """.trimIndent()
            )
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }

    fun testContainerWithForwardRef_itemWrap() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from __future__ import annotations
                from typing import List

                def f(xs: List['A']): ...

                class A:
                    def __init__(self, v): ...

                f([<caret>v])
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with A()")
            myFixture.launchAction(intention)
            myFixture.checkResult(
                """
                from __future__ import annotations
                from typing import List

                def f(xs: List['A']): ...

                class A:
                    def __init__(self, v): ...

                f([A(v)])
                """.trimIndent()
            )
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }

    fun testUnresolvedForwardRef_offersTextualWrap() {
        myFixture.configureByText(
            "a.py",
            """
            from __future__ import annotations

            def f(x: 'Ghost'): ...

            f(<caret>v)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with Ghost()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from __future__ import annotations

            def f(x: 'Ghost'): ...

            f(Ghost(v))
            """.trimIndent()
        )
    }

    // UI / label-related tests for forward refs live in WrapForwardRefsPresentationTest.
}
