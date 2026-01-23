package com.github.chbndrhnns.betterpy.features.intentions.wrap

import fixtures.TestBase
import fixtures.doIntentionTest

class WrapCallableTest : TestBase() {

    fun testWrapWithLambda_CallableExpected_ValueProvided() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Callable

            def do(t: Callable[[int], int]) -> None:
                ...

            do(<caret>1)
            """,
            """
            from typing import Callable

            def do(t: Callable[[int], int]) -> None:
                ...

            do(lambda _: 1)
            """,
            "BetterPy: Wrap with lambda"
        )
    }

    fun testWrapWithLambda_CallableNoArgsExpected_ValueProvided() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Callable

            def do(t: Callable[[], int]) -> None:
                ...

            do(<caret>1)
            """,
            """
            from typing import Callable

            def do(t: Callable[[], int]) -> None:
                ...

            do(lambda: 1)
            """,
            "BetterPy: Wrap with lambda"
        )
    }

    fun testWrapWithLambda_CallableReturnNone_ValueProvided_ShouldNotWrap() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import Callable

            def do(t: Callable[[int], None]) -> None:
                ...

            do(<caret>1)
            """
        )
        assertEmpty(
            "Intention should not be available when return type is None but value is not None",
            myFixture.filterAvailableIntentions("BetterPy: Wrap with")
        )
    }
}
