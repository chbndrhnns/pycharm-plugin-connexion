package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.PytestOutputParser
import org.junit.Assert.*
import org.junit.Test

class PytestOutputParserTest {

    @Test
    fun testParseQuietOutputWithClass() {
        val output = """
            tests/test_auth.py::TestLogin::test_success
            tests/test_auth.py::TestLogin::test_failure
            tests/test_api.py::test_health_check
        """.trimIndent()

        val items = PytestOutputParser.parseQuietOutput(output, "/project")
        assertEquals(3, items.size)
        assertEquals("tests/test_auth.py::TestLogin::test_success", items[0].nodeId)
        assertEquals("tests/test_auth.py::TestLogin::test_failure", items[1].nodeId)
        assertEquals("tests/test_api.py::test_health_check", items[2].nodeId)
    }

    @Test
    fun testParseQuietOutputWithParametrize() {
        val output = "tests/test_math.py::test_add[1-2-3]"
        val items = PytestOutputParser.parseQuietOutput(output, "/project")
        assertEquals(1, items.size)
        assertEquals("tests/test_math.py::test_add[1-2-3]", items[0].nodeId)
    }

    @Test
    fun testParseQuietOutputSkipsSeparatorLines() {
        val output = """
            ========================= test session starts =========================
            tests/test_api.py::test_health_check
            -----------------------------------------------------------------------
        """.trimIndent()

        val items = PytestOutputParser.parseQuietOutput(output, "/project")
        assertEquals(1, items.size)
        assertEquals("tests/test_api.py::test_health_check", items[0].nodeId)
    }

    @Test
    fun testParseQuietOutputEmptyInput() {
        val items = PytestOutputParser.parseQuietOutput("", "/project")
        assertEquals(0, items.size)
    }

    @Test
    fun testParseJsonOutput() {
        val json =
            """===PYTEST_COLLECTION_JSON==={"tests":[{"nodeid":"t.py::test_x","module":"t","cls":null,"name":"test_x","fixtures":["db"]}],"fixtures":{"db":{"name":"db","scope":"function","baseid":"","func_name":"db","module":"conftest","argnames":[],"autouse":false}}}===PYTEST_COLLECTION_JSON==="""
        val result = PytestOutputParser.parseJsonOutput(json)
        assertNotNull(result)
        assertEquals(1, result!!.tests.size)
        assertEquals("t.py::test_x", result.tests[0].nodeid)
        assertEquals("db", result.tests[0].fixtures[0])
        assertEquals("function", result.fixtures["db"]!!.scope)
        assertEquals(false, result.fixtures["db"]!!.autouse)
    }

    @Test
    fun testParseJsonOutputWithMultipleTests() {
        val json =
            """===PYTEST_COLLECTION_JSON==={"tests":[{"nodeid":"t.py::test_a","module":"t","cls":null,"name":"test_a","fixtures":["db"]},{"nodeid":"t.py::TestClass::test_b","module":"t","cls":"TestClass","name":"test_b","fixtures":["db","client"]}],"fixtures":{"db":{"name":"db","scope":"session","baseid":"","func_name":"db","module":"conftest","argnames":[],"autouse":false},"client":{"name":"client","scope":"function","baseid":"","func_name":"client","module":"conftest","argnames":["db"],"autouse":false}}}===PYTEST_COLLECTION_JSON==="""
        val result = PytestOutputParser.parseJsonOutput(json)
        assertNotNull(result)
        assertEquals(2, result!!.tests.size)
        assertEquals("TestClass", result.tests[1].cls)
        assertEquals(2, result.fixtures.size)
        assertEquals(listOf("db"), result.fixtures["client"]!!.argnames)
    }

    @Test
    fun testParseJsonOutputNoMarkers() {
        val result = PytestOutputParser.parseJsonOutput("some random output")
        assertNull(result)
    }

    @Test
    fun testParseJsonOutputMalformedJson() {
        val result =
            PytestOutputParser.parseJsonOutput("===PYTEST_COLLECTION_JSON==={bad json===PYTEST_COLLECTION_JSON===")
        assertNull(result)
    }

    @Test
    fun testParseJsonOutputWithSurroundingNoise() {
        val stderr = """
            INTERNALERROR> some warning
            ===PYTEST_COLLECTION_JSON==={"tests":[],"fixtures":{}}===PYTEST_COLLECTION_JSON===
            some other output
        """.trimIndent()
        val result = PytestOutputParser.parseJsonOutput(stderr)
        assertNotNull(result)
        assertEquals(0, result!!.tests.size)
        assertEquals(0, result.fixtures.size)
    }
}
