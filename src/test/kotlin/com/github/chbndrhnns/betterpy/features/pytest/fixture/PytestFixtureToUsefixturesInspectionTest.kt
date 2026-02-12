package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase

class PytestFixtureToUsefixturesInspectionTest : TestBase() {

    private val inspection = PytestFixtureToUsefixturesInspection::class.java

    fun testHighlightsUnusedFixtureParameter() {
        val code = """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(<weak_warning descr="Fixture 'my_fixture' is not used in test body, convert to @pytest.mark.usefixtures()">my_fixture</weak_warning>):
                assert True
        """.trimIndent()

        myFixture.configureByText("test_unused.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testNoHighlightWhenFixtureIsUsed() {
        val code = """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(my_fixture):
                assert my_fixture == 1
        """.trimIndent()

        myFixture.configureByText("test_used.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testNoHighlightOnNonTestFunction() {
        val code = """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.fixture
            def other_fixture(my_fixture):
                return 2
        """.trimIndent()

        myFixture.configureByText("test_fixture.py", code)
        myFixture.enableInspections(inspection)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testQuickFixConvertsToUsefixtures() {
        val code = """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(my_fixture):
                assert True
        """.trimIndent()

        myFixture.configureByText("test_fix.py", code)
        myFixture.enableInspections(inspection)
        val fixes = myFixture.getAllQuickFixes()
        val fix = fixes.find { it.familyName.contains("usefixtures") }
        assertNotNull("Expected usefixtures quickfix", fix)
        myFixture.launchAction(fix!!)
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

    fun testQuickFixAppendsToExistingUsefixtures() {
        val code = """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.fixture
            def other_fixture():
                return 2

            @pytest.mark.usefixtures("other_fixture")
            def test_something(my_fixture):
                assert True
        """.trimIndent()

        myFixture.configureByText("test_append.py", code)
        myFixture.enableInspections(inspection)
        val fixes = myFixture.getAllQuickFixes()
        val fix = fixes.find { it.familyName.contains("usefixtures") }
        assertNotNull("Expected usefixtures quickfix", fix)
        myFixture.launchAction(fix!!)
        myFixture.checkResult("""
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.fixture
            def other_fixture():
                return 2

            @pytest.mark.usefixtures("other_fixture", "my_fixture")
            def test_something():
                assert True
        """.trimIndent())
    }

    fun testQuickFixMultipleUnusedFixtures() {
        val code = """
            import pytest

            @pytest.fixture
            def fixture_a():
                return 1

            @pytest.fixture
            def fixture_b():
                return 2

            def test_something(fixture_a, fixture_b):
                assert True
        """.trimIndent()

        myFixture.configureByText("test_multi.py", code)
        myFixture.enableInspections(inspection)
        val fixes = myFixture.getAllQuickFixes()
        val usefixtureFixes = fixes.filter { it.familyName.contains("usefixtures") }
        assertEquals("Expected 2 usefixtures quickfixes", 2, usefixtureFixes.size)
        // Apply all fixes
        usefixtureFixes.forEach { myFixture.launchAction(it) }
        myFixture.checkResult("""
            import pytest

            @pytest.fixture
            def fixture_a():
                return 1

            @pytest.fixture
            def fixture_b():
                return 2

            @pytest.mark.usefixtures("fixture_a", "fixture_b")
            def test_something():
                assert True
        """.trimIndent())
    }

    fun testNoHighlightWhenSettingDisabled() {
        val code = """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(my_fixture):
                assert True
        """.trimIndent()

        myFixture.configureByText("test_disabled.py", code)
        PluginSettingsState.instance().state.enablePytestFixtureToUsefixturesInspection = false

        try {
            myFixture.enableInspections(inspection)
            val highlights = myFixture.doHighlighting()
            val warning = highlights.find { it.description?.contains("usefixtures") == true }
            assertNull("Highlight should NOT be present when setting is disabled", warning)
        } finally {
            PluginSettingsState.instance().state.enablePytestFixtureToUsefixturesInspection = true
        }
    }

    fun testNoHighlightForSelfParameter() {
        val code = """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            class TestMyClass:
                def test_something(self, my_fixture):
                    assert True
        """.trimIndent()

        myFixture.configureByText("test_self.py", code)
        myFixture.enableInspections(inspection)
        val highlights = myFixture.doHighlighting()
        // self should not be flagged, but my_fixture should be
        val selfWarning = highlights.find { it.description?.contains("'self'") == true }
        assertNull("self should not be flagged", selfWarning)
        val fixtureWarning = highlights.find { it.description?.contains("my_fixture") == true }
        assertNotNull("my_fixture should be flagged", fixtureWarning)
    }

    fun testNoHighlightForParametrizeArgument() {
        val code = """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            @pytest.mark.parametrize("arg", [1, 2])
            def test_something(arg, my_fixture):
                assert True
        """.trimIndent()

        myFixture.configureByText("test_parametrize.py", code)
        myFixture.enableInspections(inspection)
        val highlights = myFixture.doHighlighting()
        val argWarning = highlights.find { it.description?.contains("'arg'") == true }
        assertNull("parametrize argument should not be flagged", argWarning)
        val fixtureWarning = highlights.find { it.description?.contains("my_fixture") == true }
        assertNotNull("my_fixture should still be flagged", fixtureWarning)
    }

    fun testNoHighlightForParametrizeArgTuple() {
        val code = """
            import pytest

            @pytest.mark.parametrize(("a", "b"), [
                (1, 2), ])
            def test_something(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("test_parametrize_tuple.py", code)
        myFixture.enableInspections(inspection)
        val highlights = myFixture.doHighlighting()
        val aWarning = highlights.find { it.description?.contains("'a'") == true }
        assertNull("parametrize tuple argument 'a' should not be flagged", aWarning)
        val bWarning = highlights.find { it.description?.contains("'b'") == true }
        assertNull("parametrize tuple argument 'b' should not be flagged", bWarning)
    }

    fun testNoHighlightForRequestParameter() {
        val code = """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 1

            def test_something(request, my_fixture):
                assert True
        """.trimIndent()

        myFixture.configureByText("test_request.py", code)
        myFixture.enableInspections(inspection)
        val highlights = myFixture.doHighlighting()
        // request should not be flagged
        val requestWarning = highlights.find { it.description?.contains("'request'") == true }
        assertNull("request should not be flagged", requestWarning)
    }
}
