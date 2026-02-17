package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.PytestMarkersParser
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.RegisteredMarker
import org.junit.Assert.*
import org.junit.Test

class PytestMarkersParserTest {

    @Test
    fun `parse empty output returns empty list`() {
        val result = PytestMarkersParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse single marker with description`() {
        val output = "@pytest.mark.slow: mark test as slow."
        val result = PytestMarkersParser.parse(output)

        assertEquals(1, result.size)
        assertEquals("slow", result[0].name)
        assertEquals("mark test as slow.", result[0].description)
    }

    @Test
    fun `parse multiple markers`() {
        val output = """
            @pytest.mark.slow: mark test as slow.
            @pytest.mark.integration: mark test as integration test.
            @pytest.mark.skip(reason=None): skip the given test function with an optional reason.
        """.trimIndent()

        val result = PytestMarkersParser.parse(output)

        assertEquals(3, result.size)
        assertEquals("slow", result[0].name)
        assertEquals("integration", result[1].name)
        assertEquals("skip(reason=None)", result[2].name)
    }

    @Test
    fun `parse marker with parameters in name`() {
        val output =
            "@pytest.mark.skipif(condition, *, reason=None): skip the given test function if eval(condition) results in a True value."
        val result = PytestMarkersParser.parse(output)

        assertEquals(1, result.size)
        assertEquals("skipif(condition, *, reason=None)", result[0].name)
        assertEquals("skip the given test function if eval(condition) results in a True value.", result[0].description)
    }

    @Test
    fun `parse ignores non-marker lines`() {
        val output = """
            =========================== test session starts ===========================
            @pytest.mark.slow: mark test as slow.
            
            some other output
        """.trimIndent()

        val result = PytestMarkersParser.parse(output)

        assertEquals(1, result.size)
        assertEquals("slow", result[0].name)
    }

    @Test
    fun `parse marker without description`() {
        val output = "@pytest.mark.custom:"
        val result = PytestMarkersParser.parse(output)

        assertEquals(1, result.size)
        assertEquals("custom", result[0].name)
        assertEquals("", result[0].description)
    }

    @Test
    fun `parse multiline description`() {
        val output = """
            @pytest.mark.parametrize(argnames, argvalues): call a test function multiple times passing in different arguments in turn. argvalues generally needs to be a list of values if argnames specifies only one name or a list of tuples of values if argnames specifies multiple names.
        """.trimIndent()

        val result = PytestMarkersParser.parse(output)

        assertEquals(1, result.size)
        assertEquals("parametrize(argnames, argvalues)", result[0].name)
        assertTrue(result[0].description.contains("call a test function multiple times"))
    }

    @Test
    fun `parse builtin markers`() {
        val output = """
            @pytest.mark.filterwarnings(warning): add a warning filter to the given test.
            @pytest.mark.skip(reason=None): skip the given test function with an optional reason.
            @pytest.mark.skipif(condition, *, reason=None): skip the given test function if eval(condition) results in a True value.
            @pytest.mark.xfail(condition, ..., *, reason=None, run=True, raises=None, strict=xfail_strict): mark the test function as an expected failure if eval(condition) results in a True value.
            @pytest.mark.parametrize(argnames, argvalues): call a test function multiple times passing in different arguments in turn.
            @pytest.mark.usefixtures(fixturename1, fixturename2, ...): mark tests as needing all of the specified fixtures.
        """.trimIndent()

        val result = PytestMarkersParser.parse(output)

        assertEquals(6, result.size)
        val names = result.map { it.name.substringBefore("(") }
        assertTrue(names.contains("filterwarnings"))
        assertTrue(names.contains("skip"))
        assertTrue(names.contains("skipif"))
        assertTrue(names.contains("xfail"))
        assertTrue(names.contains("parametrize"))
        assertTrue(names.contains("usefixtures"))
    }

    @Test
    fun `isBuiltin returns true for builtin markers`() {
        val builtins = listOf("skip", "skipif", "xfail", "parametrize", "usefixtures", "filterwarnings")
        for (name in builtins) {
            val marker = RegisteredMarker(name, "description")
            assertTrue("$name should be builtin", marker.isBuiltin)
        }
    }

    @Test
    fun `isBuiltin returns false for custom markers`() {
        val marker = RegisteredMarker("slow", "mark test as slow")
        assertFalse(marker.isBuiltin)
    }
}
