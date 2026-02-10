package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.PytestOutputParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests using real pytest --collect-only output captured from a Python 3.13 / pytest 9.0.2 environment.
 */
class PytestOutputParserIntegrationTest {

    private fun loadResource(name: String): String =
        javaClass.getResource("/testData/$name")?.readText()
            ?: error("Test resource not found: $name")

    @Test
    fun `parse real quiet output - correct item count`() {
        val stdout = loadResource("pytest-collect-quiet-output.txt")
        val items = PytestOutputParser.parseQuietOutput(stdout, "/project")
        assertEquals("Should find 5 test items", 5, items.size)
    }

    @Test
    fun `parse real quiet output - class method node IDs`() {
        val stdout = loadResource("pytest-collect-quiet-output.txt")
        val items = PytestOutputParser.parseQuietOutput(stdout, "/project")
        val nodeIds = items.map { it.nodeId }
        assertTrue("Should contain class method test", nodeIds.any { it.contains("TestLogin::test_success") })
        assertTrue("Should contain class method test", nodeIds.any { it.contains("TestLogin::test_failure") })
    }

    @Test
    fun `parse real quiet output - standalone function`() {
        val stdout = loadResource("pytest-collect-quiet-output.txt")
        val items = PytestOutputParser.parseQuietOutput(stdout, "/project")
        assertTrue("Should contain standalone test", items.any { it.nodeId.contains("::test_standalone") })
    }

    @Test
    fun `parse real quiet output - parametrized tests`() {
        val stdout = loadResource("pytest-collect-quiet-output.txt")
        val items = PytestOutputParser.parseQuietOutput(stdout, "/project")
        val paramTests = items.filter { it.nodeId.contains("test_add") }
        assertEquals("Should find 2 parametrized variants", 2, paramTests.size)
        assertTrue("Should have param ID in brackets", paramTests[0].nodeId.contains("[1-2-3]"))
        assertTrue("Should have param ID in brackets", paramTests[1].nodeId.contains("[4-5-9]"))
    }

    @Test
    fun `parse real quiet output - filters summary line`() {
        val stdout = loadResource("pytest-collect-quiet-output.txt")
        val items = PytestOutputParser.parseQuietOutput(stdout, "/project")
        assertFalse("Should not include summary line", items.any { it.nodeId.contains("collected") })
    }

    @Test
    fun `parse real JSON output - correct test count`() {
        val stderr = loadResource("pytest-collect-json-output.txt")
        val result = PytestOutputParser.parseJsonOutput(stderr)
        assertNotNull("Should parse JSON output", result)
        assertEquals("Should find 5 tests", 5, result!!.tests.size)
    }

    @Test
    fun `parse real JSON output - test with class`() {
        val stderr = loadResource("pytest-collect-json-output.txt")
        val result = PytestOutputParser.parseJsonOutput(stderr)!!
        val classTest = result.tests.first { it.name == "test_success" }
        assertEquals("TestLogin", classTest.cls)
        assertEquals("test_auth", classTest.module)
        assertTrue("Should include fixture deps", classTest.fixtures.contains("db_session"))
    }

    @Test
    fun `parse real JSON output - standalone test`() {
        val stderr = loadResource("pytest-collect-json-output.txt")
        val result = PytestOutputParser.parseJsonOutput(stderr)!!
        val standalone = result.tests.first { it.name == "test_standalone" }
        assertNull("Standalone test should have no class", standalone.cls)
        assertTrue("Should depend on settings fixture", standalone.fixtures.contains("settings"))
    }

    @Test
    fun `parse real JSON output - user-defined fixtures`() {
        val stderr = loadResource("pytest-collect-json-output.txt")
        val result = PytestOutputParser.parseJsonOutput(stderr)!!
        val userFixtures = result.fixtures.filter { it.value.module == "conftest" }
        assertEquals("Should find 4 user-defined fixtures", 4, userFixtures.size)
        assertTrue("Should include db_engine", userFixtures.containsKey("db_engine"))
        assertTrue("Should include db_session", userFixtures.containsKey("db_session"))
        assertTrue("Should include settings", userFixtures.containsKey("settings"))
        assertTrue("Should include cleanup", userFixtures.containsKey("cleanup"))
    }

    @Test
    fun `parse real JSON output - fixture scopes`() {
        val stderr = loadResource("pytest-collect-json-output.txt")
        val result = PytestOutputParser.parseJsonOutput(stderr)!!
        assertEquals("function", result.fixtures["db_engine"]!!.scope)
        assertEquals("function", result.fixtures["db_session"]!!.scope)
        assertEquals("module", result.fixtures["settings"]!!.scope)
    }

    @Test
    fun `parse real JSON output - fixture dependencies`() {
        val stderr = loadResource("pytest-collect-json-output.txt")
        val result = PytestOutputParser.parseJsonOutput(stderr)!!
        assertEquals(
            "db_session should depend on db_engine",
            listOf("db_engine"),
            result.fixtures["db_session"]!!.argnames
        )
        assertEquals("db_engine should have no deps", emptyList<String>(), result.fixtures["db_engine"]!!.argnames)
    }

    @Test
    fun `parse real JSON output - autouse flag`() {
        val stderr = loadResource("pytest-collect-json-output.txt")
        val result = PytestOutputParser.parseJsonOutput(stderr)!!
        assertTrue("cleanup should be autouse", result.fixtures["cleanup"]!!.autouse)
        assertFalse("db_engine should not be autouse", result.fixtures["db_engine"]!!.autouse)
        assertFalse("db_session should not be autouse", result.fixtures["db_session"]!!.autouse)
        assertFalse("settings should not be autouse", result.fixtures["settings"]!!.autouse)
    }

    @Test
    fun `parse real JSON output - builtin fixtures present`() {
        val stderr = loadResource("pytest-collect-json-output.txt")
        val result = PytestOutputParser.parseJsonOutput(stderr)!!
        assertTrue("Should include builtin tmp_path", result.fixtures.containsKey("tmp_path"))
        assertTrue("Should include builtin monkeypatch", result.fixtures.containsKey("monkeypatch"))
        assertTrue("Should include builtin capsys", result.fixtures.containsKey("capsys"))
    }
}
