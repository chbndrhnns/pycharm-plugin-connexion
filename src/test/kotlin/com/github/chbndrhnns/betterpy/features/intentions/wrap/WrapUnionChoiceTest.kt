package com.github.chbndrhnns.betterpy.features.intentions.wrap

import fixtures.TestBase
import fixtures.doIntentionTest
import fixtures.withWrapPopupSelection

/**
 * Wrap intention behavior when the expected type is a union (without forward refs).
 */
class WrapUnionChoiceTest : TestBase() {

    fun testUnionAutoSelect_PathOverStr() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from pathlib import Path

            def f(p: int | Path) -> None:
                pass

            f(<caret>"val")
            """,
            """
            from pathlib import Path

            def f(p: int | Path) -> None:
                pass

            f(Path("val"))
            """,
            "BetterPy: Wrap with Path()"
        )
    }

    fun testUnionChooser_WithTypingUnionContainingOr() {
        withWrapPopupSelection(0) {
            myFixture.doIntentionTest(
                "a.py",
                """
                from typing import NewType, Union
    
                One = NewType("One", str)
                Two = NewType("Two", str)
    
                a: Union[One | Two] = <caret>"val"
                """,
                """
                from typing import NewType, Union
    
                One = NewType("One", str)
                Two = NewType("Two", str)
    
                a: Union[One | Two] = One("val")
                """,
                "BetterPy: Wrap with expected union type…"
            )
        }
    }
}
