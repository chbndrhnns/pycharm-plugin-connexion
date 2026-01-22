package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import fixtures.TestBase
import fixtures.doIntentionTest

class TogglePytestSkipIntentionSimpleListTest : TestBase() {

    fun testCommentOutSimpleParamInFixture() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.fixture(
                params=[
                    1,
                    <caret>2,
                    3,
                ],
            )
            def foo(): pass
            """,
            """
            import pytest

            @pytest.fixture(
                params=[
                    1,
                    # 2,
                    3,
                ],
            )
            def foo(): pass
            """,
            "BetterPy: Toggle pytest skip (param value)"
        )
    }

    fun testUncommentSimpleParamInFixture() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.fixture(
                params=[
                    1,
                    # <caret>2,
                    3,
                ],
            )
            def foo(): pass
            """,
            """
            import pytest

            @pytest.fixture(
                params=[
                    1,
                    2,
                    3,
                ],
            )
            def foo(): pass
            """,
            "BetterPy: Toggle pytest skip (param value)"
        )
    }

    fun testCommentOutTupleParamInParametrize() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.mark.parametrize("x, y", [
                (1, 1),
                <caret>(2, 2),
                (3, 3),
            ])
            def test_foo(x, y):
                pass
            """,
            """
            import pytest

            @pytest.mark.parametrize("x, y", [
                (1, 1),
                # (2, 2),
                (3, 3),
            ])
            def test_foo(x, y):
                pass
            """,
            "BetterPy: Toggle pytest skip (param value)"
        )
    }

    fun testCommentOutMultiLineTupleParam() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.mark.parametrize("x, y", [
                (1, 1),
                <caret>(
                    2,
                    2
                ),
                (3, 3),
            ])
            def test_foo(x, y):
                pass
            """,
            """
            import pytest

            @pytest.mark.parametrize("x, y", [
                (1, 1),
                # (
                #     2,
                #     2
                # ),
                (3, 3),
            ])
            def test_foo(x, y):
                pass
            """,
            "BetterPy: Toggle pytest skip (param value)"
        )
    }

    fun testUncommentMultiLineTupleParam() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.mark.parametrize("x, y", [
                (1, 1),
                # <caret>(
                #     2,
                #     2
                # ),
                (3, 3),
            ])
            def test_foo(x, y):
                pass
            """,
            """
            import pytest

            @pytest.mark.parametrize("x, y", [
                (1, 1),
                (
                    2,
                    2
                ),
                (3, 3),
            ])
            def test_foo(x, y):
                pass
            """,
            "BetterPy: Toggle pytest skip (param value)"
        )
    }

    fun testNotAvailableOnSameLine() {
        myFixture.configureByText(
            "test_foo.py", """
            import pytest

            @pytest.fixture(
                params=[1, 2<caret>, 3],
            )
            def foo(): pass
        """.trimIndent()
        )

        val intentions = myFixture.availableIntentions.filter { it.familyName == "BetterPy: Toggle pytest skip" }
        assertEmpty(intentions)
    }
}
