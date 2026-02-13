package com.github.chbndrhnns.betterpy.features.pytest.parametrize

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase

class PytestParametrizeCountMismatchInspectionTest : TestBase() {

    private val inspection = PytestParametrizeCountMismatchInspection::class.java

    fun testHighlightsMismatchedTupleCount() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a, b", [
                (1, 2),
                <warning descr="Expected 2 values per parameter set (a, b), but got 3">(1, 2, 3)</warning>,
            ])
            def test_something(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("test_mismatch.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testNoHighlightWhenCountsMatch() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a, b", [
                (1, 2),
                (3, 4),
            ])
            def test_something(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("test_match.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testHighlightsTooFewValues() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a, b, c", [
                <warning descr="Expected 3 values per parameter set (a, b, c), but got 2">(1, 2)</warning>,
            ])
            def test_something(a, b, c):
                pass
        """.trimIndent()

        myFixture.configureByText("test_too_few.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testNoHighlightForSingleParameter() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a", [1, 2, 3])
            def test_something(a):
                pass
        """.trimIndent()

        myFixture.configureByText("test_single.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testHighlightsPytestParamMismatch() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a, b", [
                pytest.param(1, 2),
                <warning descr="Expected 2 values per parameter set (a, b), but got 3">pytest.param(1, 2, 3)</warning>,
            ])
            def test_something(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("test_param.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testPytestParamWithKeywordArgsNotCounted() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a, b", [
                pytest.param(1, 2, id="case1"),
            ])
            def test_something(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("test_param_kw.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testNoHighlightWhenSettingDisabled() {
        val code = """
            import pytest

            @pytest.mark.parametrize("a, b", [
                (1, 2, 3),
            ])
            def test_something(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("test_disabled.py", code)
        PluginSettingsState.instance().state.enablePytestParametrizeCountMismatchInspection = false

        try {
            myFixture.enableInspections(inspection)
            val highlights = myFixture.doHighlighting()
            val warning = highlights.find { it.description?.contains("Expected") == true }
            assertNull("Highlight should NOT be present when setting is disabled", warning)
        } finally {
            PluginSettingsState.instance().state.enablePytestParametrizeCountMismatchInspection = true
        }
    }

    fun testParameterNamesAsList() {
        val code = """
            import pytest

            @pytest.mark.parametrize(["a", "b"], [
                (1, 2),
                <warning descr="Expected 2 values per parameter set (a, b), but got 1">(1,)</warning>,
            ])
            def test_something(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("test_list_names.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }
}
