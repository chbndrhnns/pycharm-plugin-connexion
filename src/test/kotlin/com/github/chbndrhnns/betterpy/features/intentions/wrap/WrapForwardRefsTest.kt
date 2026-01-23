package com.github.chbndrhnns.betterpy.features.intentions.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest
import fixtures.withWrapPopupSelection

/**
 * Wrap intention behavior for forward-referenced types.
 */
class WrapForwardRefsTest : TestBase() {

    fun testNonContainerForwardRef_param() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from __future__ import annotations

            def f(x: 'A') -> None:
                ...

            class A:
                def __init__(self, value): ...

            f(<caret>value)
            """,
            """
            from __future__ import annotations

            def f(x: 'A') -> None:
                ...

            class A:
                def __init__(self, value): ...

            f(A(value))
            """,
            "BetterPy: Wrap with A()"
        )
    }

    fun testUnionWithForwardRef_mixed() {
        withWrapPopupSelection(0) { fake ->
            myFixture.doIntentionTest(
                "a.py",
                """
                from __future__ import annotations

                class B:
                    def __init__(self, v): ...

                def f(x: 'A' | B): ...

                class A:
                    def __init__(self, v): ...

                f(<caret>v)
                """,
                """
                from __future__ import annotations

                class B:
                    def __init__(self, v): ...

                def f(x: 'A' | B): ...

                class A:
                    def __init__(self, v): ...

                f(A(v))
                """,
                "BetterPy: Wrap with expected union type…"
            )
            // We should offer both A and B; order is not strictly important, but A first is fine
            assertTrue(fake.lastLabels.any { it.startsWith("A") })
            assertTrue(fake.lastLabels.any { it.startsWith("B") })
        }
    }

    fun testContainerWithForwardRef_itemWrap() {
        withWrapPopupSelection(0) {
            myFixture.doIntentionTest(
                "a.py",
                """
                from __future__ import annotations
                from typing import List

                def f(xs: List['A']): ...

                class A:
                    def __init__(self, v): ...

                f([<caret>v])
                """,
                """
                from __future__ import annotations
                from typing import List

                def f(xs: List['A']): ...

                class A:
                    def __init__(self, v): ...

                f([A(v)])
                """,
                "BetterPy: Wrap with A()"
            )
        }
    }

    fun testUnresolvedForwardRef_offersTextualWrap() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from __future__ import annotations

            def f(x: 'Ghost'): ...

            f(<caret>v)
            """,
            """
            from __future__ import annotations

            def f(x: 'Ghost'): ...

            f(Ghost(v))
            """,
            "BetterPy: Wrap with Ghost()"
        )
    }

    fun testParameterDefault_UnionForwardRef_NoWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def do(val: "int | str | None" = <caret>2):
                pass
            """,
            "BetterPy: Wrap with"
        )
    }

    fun testAssignment_UnionForwardRef_NoWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            val: "int | str | None" = <caret>2
            """,
            "BetterPy: Wrap with"
        )
    }
}
