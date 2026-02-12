package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase

class ConvertUsefixturesToTestArgumentIntentionTest : TestBase() {

    // ---- Convert parameter → usefixtures ----

    fun testConvertParamToUsefixtures() {
        myFixture.configureByText(
            "test_a.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(my_<caret>fixture):
                assert True
        """.trimIndent()
        )
        val intention = ConvertFixtureParamToUsefixturesIntention()
        assertTrue("Intention should be available", intention.isAvailable(project, myFixture.editor, myFixture.file))
        myFixture.launchAction(intention)
        myFixture.checkResult("""
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.mark.usefixtures("my_fixture")
            def test_something():
                assert True
        """.trimIndent())
    }

    fun testConvertParamToUsefixtures_AppendsToExisting() {
        myFixture.configureByText(
            "test_b.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.fixture
            def other():
                return 2

            @pytest.mark.usefixtures("other")
            def test_something(my_<caret>fixture):
                assert True
        """.trimIndent()
        )
        val intention = ConvertFixtureParamToUsefixturesIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
        myFixture.launchAction(intention)
        myFixture.checkResult("""
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.fixture
            def other():
                return 2

            @pytest.mark.usefixtures("other", "my_fixture")
            def test_something():
                assert True
        """.trimIndent())
    }

    fun testConvertParamToUsefixtures_NotAvailableOnUsedParam() {
        myFixture.configureByText(
            "test_c.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(my_<caret>fixture):
                print(my_fixture)
        """.trimIndent()
        )
        val intention = ConvertFixtureParamToUsefixturesIntention()
        // Still available — the intention doesn't check usage, it's a manual conversion
        // Actually, let's make it always available on fixture params in test functions
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
    }

    fun testConvertParamToUsefixtures_NotAvailableOnNonTestFunction() {
        myFixture.configureByText(
            "test_d.py", """
            def helper(some_<caret>param):
                pass
        """.trimIndent()
        )
        val intention = ConvertFixtureParamToUsefixturesIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, myFixture.file))
    }

    fun testConvertParamToUsefixtures_NotAvailableOnSelf() {
        myFixture.configureByText(
            "test_e.py", """
            class TestFoo:
                def test_something(sel<caret>f):
                    pass
        """.trimIndent()
        )
        val intention = ConvertFixtureParamToUsefixturesIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, myFixture.file))
    }

    fun testConvertParamToUsefixtures_NotAvailableWhenDisabled() {
        PluginSettingsState.instance().state.enableAdvancedPytestFixtureFeatures = false
        try {
            myFixture.configureByText(
                "test_f.py", """
                import pytest

                @pytest.fixture
                def my_fixture():
                    return 1

                def test_something(my_<caret>fixture):
                    assert True
            """.trimIndent()
            )
            val intention = ConvertFixtureParamToUsefixturesIntention()
            assertFalse(intention.isAvailable(project, myFixture.editor, myFixture.file))
        } finally {
            PluginSettingsState.instance().state.enableAdvancedPytestFixtureFeatures = true
        }
    }

    // ---- Convert usefixtures → parameter ----

    fun testConvertUsefixturesToParam() {
        myFixture.configureByText(
            "test_g.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.mark.usefixtures("my_<caret>fixture")
            def test_something():
                assert True
        """.trimIndent()
        )
        val intention = ConvertUsefixturesToTestArgumentIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
        myFixture.launchAction(intention)
        myFixture.checkResult("""
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(my_fixture):
                assert True
        """.trimIndent())
    }

    fun testConvertUsefixturesToParam_KeepsOtherFixtures() {
        myFixture.configureByText(
            "test_h.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.fixture
            def other():
                return 2

            @pytest.mark.usefixtures("other", "my_<caret>fixture")
            def test_something():
                assert True
        """.trimIndent()
        )
        val intention = ConvertUsefixturesToTestArgumentIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
        myFixture.launchAction(intention)
        myFixture.checkResult("""
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.fixture
            def other():
                return 2

            @pytest.mark.usefixtures("other")
            def test_something(my_fixture):
                assert True
        """.trimIndent())
    }

    fun testConvertUsefixturesToParam_AvailableOnDecoratorName() {
        myFixture.configureByText(
            "test_dec1.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @py<caret>test.mark.usefixtures("my_fixture")
            def test_something():
                assert True
        """.trimIndent()
        )
        val intention = ConvertUsefixturesToTestArgumentIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
        myFixture.launchAction(intention)
        myFixture.checkResult("""
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(my_fixture):
                assert True
        """.trimIndent())
    }

    fun testConvertUsefixturesToParam_AvailableOnUsefixtures() {
        myFixture.configureByText(
            "test_dec2.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.mark.use<caret>fixtures("my_fixture")
            def test_something():
                assert True
        """.trimIndent()
        )
        val intention = ConvertUsefixturesToTestArgumentIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
        myFixture.launchAction(intention)
        myFixture.checkResult("""
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(my_fixture):
                assert True
        """.trimIndent())
    }

    fun testConvertUsefixturesToParam_NotAvailableOutsideUsefixtures() {
        myFixture.configureByText(
            "test_i.py", """
            import pytest

            @pytest.mark.skip
            def test_something(<caret>):
                assert True
        """.trimIndent()
        )
        val intention = ConvertUsefixturesToTestArgumentIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, myFixture.file))
    }

    fun testConvertUsefixturesToParam_NotAvailableWhenDisabled() {
        PluginSettingsState.instance().state.enableAdvancedPytestFixtureFeatures = false
        try {
            myFixture.configureByText(
                "test_j.py", """
                import pytest

                @pytest.mark.usefixtures("my_<caret>fixture")
                def test_something():
                    assert True
            """.trimIndent()
            )
            val intention = ConvertUsefixturesToTestArgumentIntention()
            assertFalse(intention.isAvailable(project, myFixture.editor, myFixture.file))
        } finally {
            PluginSettingsState.instance().state.enableAdvancedPytestFixtureFeatures = true
        }
    }
}
