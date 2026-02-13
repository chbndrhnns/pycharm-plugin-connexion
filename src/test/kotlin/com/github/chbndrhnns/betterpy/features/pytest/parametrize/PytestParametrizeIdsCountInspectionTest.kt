package com.github.chbndrhnns.betterpy.features.pytest.parametrize

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase

class PytestParametrizeIdsCountInspectionTest : TestBase() {

    private val inspection = PytestParametrizeIdsCountInspection::class.java

    fun testHighlightsIdsMismatch() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a, b", [
                (1, 2),
                (3, 4),
            ], ids=<warning descr="Expected 2 ids (one per parameter set), but got 3">["first", "second", "third"]</warning>)
            def test_something(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("test_ids_mismatch.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testNoHighlightWhenIdsCountMatches() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a, b", [
                (1, 2),
                (3, 4),
            ], ids=["first", "second"])
            def test_something(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("test_ids_match.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testHighlightsIdsTooFew() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a", [1, 2, 3], ids=<warning descr="Expected 3 ids (one per parameter set), but got 1">["only_one"]</warning>)
            def test_something(a):
                pass
        """.trimIndent()

        myFixture.configureByText("test_ids_too_few.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testSkipsDynamicallyCalculatedIds() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a", [1, 2, 3], ids=lambda x: str(x))
            def test_something(a):
                pass
        """.trimIndent()

        myFixture.configureByText("test_ids_dynamic.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testSkipsFunctionCallIds() {
        val code = """
            import pytest

            def make_ids():
                return ["a", "b"]

            @pytest.mark.parametrize("a", [1, 2], ids=make_ids())
            def test_something(a):
                pass
        """.trimIndent()

        myFixture.configureByText("test_ids_func_call.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testFollowsReferenceToList() {
        val code = """
            import pytest

            my_ids = ["first", "second", "third"]

            @pytest.mark.parametrize("a", [1, 2], ids=<warning descr="Expected 2 ids (one per parameter set), but got 3">my_ids</warning>)
            def test_something(a):
                pass
        """.trimIndent()

        myFixture.configureByText("test_ids_ref.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testFollowsReferenceMatching() {
        val code = """
            import pytest

            my_ids = ["first", "second"]

            @pytest.mark.parametrize("a", [1, 2], ids=my_ids)
            def test_something(a):
                pass
        """.trimIndent()

        myFixture.configureByText("test_ids_ref_match.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testNoHighlightWhenSettingDisabled() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a", [1, 2, 3], ids=["only_one"])
            def test_something(a):
                pass
        """.trimIndent()

        myFixture.configureByText("test_ids_disabled.py", code)
        PluginSettingsState.instance().state.enablePytestParametrizeIdsCountInspection = false

        try {
            myFixture.enableInspections(inspection)
            val highlights = myFixture.doHighlighting()
            val warning = highlights.find { it.description?.contains("ids") == true }
            assertNull("Highlight should NOT be present when setting is disabled", warning)
        } finally {
            PluginSettingsState.instance().state.enablePytestParametrizeIdsCountInspection = true
        }
    }

    fun testNoHighlightWithoutIds() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a", [1, 2, 3])
            def test_something(a):
                pass
        """.trimIndent()

        myFixture.configureByText("test_no_ids.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testIdsTupleExpression() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a", [1, 2], ids=<warning descr="Expected 2 ids (one per parameter set), but got 3">("first", "second", "third")</warning>)
            def test_something(a):
                pass
        """.trimIndent()

        myFixture.configureByText("test_ids_tuple.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }
}
