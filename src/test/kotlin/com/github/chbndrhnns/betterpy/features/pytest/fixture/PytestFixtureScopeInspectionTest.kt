package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase
import fixtures.doInspectionTest

class PytestFixtureScopeInspectionTest : TestBase() {

    private val inspection = PytestFixtureScopeInspection::class.java
    private val fixName = "BetterPy: Apply fixture scope \"session\" to fixture 'dep'"

    fun testApplyParentScopeQuickFix() {
        myFixture.doInspectionTest(
            "inspections/PytestFixtureScopeInspection/ApplyParentScope/test.py",
            inspection,
            fixName,
            "inspections/PytestFixtureScopeInspection/ApplyParentScope/test_after.py",
            checkHighlighting = false
        )
    }

    fun testHighlightsNarrowerScope() {
        val code = """
            import pytest

            @pytest.fixture(scope="session")
            def abcdefg(dep):
                return 1

            @pytest.fixture
            def dep():
                return 2

            def test(abcdefg):
                assert abcdefg
        """.trimIndent()

        myFixture.configureByText("test_scope.py", code)
        myFixture.enableInspections(inspection)
        val highlights = myFixture.doHighlighting()

        val warning = highlights.find { it.description?.contains("Fixture dependency") == true }
        assertNotNull("Expected fixture scope warning", warning)
    }

    fun testNoHighlightWhenSettingDisabled() {
        val code = """
            import pytest

            @pytest.fixture(scope="session")
            def abcdefg(dep):
                return 1

            @pytest.fixture
            def dep():
                return 2

            def test(abcdefg):
                assert abcdefg
        """.trimIndent()

        myFixture.configureByText("test_disabled.py", code)
        PluginSettingsState.instance().state.enablePytestFixtureScopeInspection = false

        try {
            myFixture.enableInspections(inspection)
            val highlights = myFixture.doHighlighting()
            val warning = highlights.find { it.description?.contains("Fixture dependency") == true }
            assertNull("Highlight should NOT be present when setting is disabled", warning)
        } finally {
            PluginSettingsState.instance().state.enablePytestFixtureScopeInspection = true
        }
    }
}
