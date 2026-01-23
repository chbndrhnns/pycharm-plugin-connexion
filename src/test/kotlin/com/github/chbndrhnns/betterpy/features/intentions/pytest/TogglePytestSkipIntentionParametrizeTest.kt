package com.github.chbndrhnns.betterpy.features.intentions.pytest

import fixtures.TestBase
import fixtures.doIntentionTest

class TogglePytestSkipIntentionParametrizeTest : TestBase() {

    fun testAddSkipToParamWithExistingMarksList() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.fixture(
                params=[
                    pytest.param(
                        "foo",
                        marks=[
                            pytest.mark.io<caret>
                        ],
                    ),
                ],
            )
            def foo(): pass
            """,
            """
            import pytest

            @pytest.fixture(
                params=[
                    pytest.param(
                        "foo",
                        marks=[pytest.mark.io, pytest.mark.skip, ],
                    ),
                ],
            )
            def foo(): pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testRemoveSkipFromParamWithExistingMarksList() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.fixture(
                params=[
                    pytest.param(
                        "foo",
                        marks=[
                            pytest.mark.io,
                            pytest.mark.skip<caret>
                        ],
                    ),
                ],
            )
            def foo(): pass
            """,
            """
            import pytest

            @pytest.fixture(
                params=[
                    pytest.param(
                        "foo",
                        marks=[pytest.mark.io],
                    ),
                ],
            )
            def foo(): pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testAddSkipToParamWithoutMarks() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.fixture(
                params=[
                    pytest.param(
                        "foo"<caret>
                    ),
                ],
            )
            def foo(): pass
            """,
            """
            import pytest

            @pytest.fixture(
                params=[
                    pytest.param("foo", marks=[pytest.mark.skip, ]),
                ],
            )
            def foo(): pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }
}
