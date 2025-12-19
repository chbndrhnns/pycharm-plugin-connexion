package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import fixtures.TestBase
import fixtures.doIntentionTest

class TogglePytestSkipIntentionNestedParamTest : TestBase() {

    fun testSkipTupleWhenCaretIsInside() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.mark.parametrize("a,b", [
                (1, 2),
                (3, 4<caret>)
            ])
            def test_something(a, b):
                pass
            """,
            """
            import pytest

            @pytest.mark.parametrize("a,b", [
                (1, 2),
                # (3, 4)
            ])
            def test_something(a, b):
                pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testUnskipTupleWhenCaretIsInsideComment() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.mark.parametrize("a,b", [
                (1, 2),
                # (3, 4<caret>)
            ])
            def test_something(a, b):
                pass
            """,
            """
            import pytest

            @pytest.mark.parametrize("a,b", [
                (1, 2),
                (3, 4)
            ])
            def test_something(a, b):
                pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testSkipListWhenCaretIsInside() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.mark.parametrize("a,b", [
                [1, 2],
                [3, 4<caret>]
            ])
            def test_something(a, b):
                pass
            """,
            """
            import pytest

            @pytest.mark.parametrize("a,b", [
                [1, 2],
                # [3, 4]
            ])
            def test_something(a, b):
                pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }
}
