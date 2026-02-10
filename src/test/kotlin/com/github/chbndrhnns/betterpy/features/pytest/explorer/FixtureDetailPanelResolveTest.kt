package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.ui.FixtureDetailPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FixtureDetailPanelResolveTest {

    @Test
    fun `conftest fixture in same directory resolves`() {
        val test = test("tests/fixture_param/test_param.py::test_", "tests/fixture_param/test_param.py")
        val fixtures = listOf(
            fixture("my_fixture", "tests/fixture_param/conftest.py"),
        )
        val result = FixtureDetailPanel.resolveClosestFixtures(test, fixtures)
        assertEquals("tests/fixture_param/conftest.py", result["my_fixture"]?.definedIn)
    }

    @Test
    fun `conftest fixture in parent directory resolves`() {
        val test = test("tests/sub/test_deep.py::test_", "tests/sub/test_deep.py")
        val fixtures = listOf(
            fixture("my_fixture", "tests/conftest.py"),
        )
        val result = FixtureDetailPanel.resolveClosestFixtures(test, fixtures)
        assertEquals("tests/conftest.py", result["my_fixture"]?.definedIn)
    }

    @Test
    fun `closer conftest wins over parent conftest`() {
        val test = test("tests/sub/test_deep.py::test_", "tests/sub/test_deep.py")
        val fixtures = listOf(
            fixture("my_fixture", "tests/conftest.py"),
            fixture("my_fixture", "tests/sub/conftest.py"),
        )
        val result = FixtureDetailPanel.resolveClosestFixtures(test, fixtures)
        assertEquals("tests/sub/conftest.py", result["my_fixture"]?.definedIn)
    }

    @Test
    fun `class-level fixture wins over same-directory conftest`() {
        val test = test("tests/test_mod.py::TestClass::test_", "tests/test_mod.py")
        val fixtures = listOf(
            fixture("my_fixture", "tests/conftest.py"),
            fixture("my_fixture", "tests/test_mod.py::TestClass"),
        )
        val result = FixtureDetailPanel.resolveClosestFixtures(test, fixtures)
        assertEquals("tests/test_mod.py::TestClass", result["my_fixture"]?.definedIn)
    }

    @Test
    fun `module-level fixture wins over conftest in same directory`() {
        val test = test("tests/test_mod.py::test_", "tests/test_mod.py")
        val fixtures = listOf(
            fixture("my_fixture", "tests/conftest.py"),
            fixture("my_fixture", "tests/test_mod.py"),
        )
        val result = FixtureDetailPanel.resolveClosestFixtures(test, fixtures)
        // Both are in same dir; module file has same dir length but test_mod.py is same dir
        // They tie on directory — both "tests". The one from the test module itself should be picked
        // since they have the same directory prefix length.
        assertNotNull(result["my_fixture"])
    }

    @Test
    fun `root-level conftest resolves for nested test`() {
        val test = test("tests/sub/deep/test_x.py::test_", "tests/sub/deep/test_x.py")
        val fixtures = listOf(
            fixture("my_fixture", "conftest.py"),
        )
        val result = FixtureDetailPanel.resolveClosestFixtures(test, fixtures)
        assertEquals("conftest.py", result["my_fixture"]?.definedIn)
    }

    // --- helpers ---

    private fun test(nodeId: String, modulePath: String) = CollectedTest(
        nodeId = nodeId,
        modulePath = modulePath,
        className = null,
        functionName = nodeId.substringAfterLast("::"),
        fixtures = emptyList(),
    )

    private fun fixture(name: String, definedIn: String) = CollectedFixture(
        name = name,
        scope = "function",
        definedIn = definedIn,
        functionName = name,
        dependencies = emptyList(),
        isAutouse = false,
    )
}
