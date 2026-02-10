package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.PytestCollectorTask
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PytestCollectorTask command-line construction and exit-code handling.
 * These tests do NOT run actual pytest processes — they test the logic in isolation.
 */
class PytestCollectorTaskTest {

    @Test
    fun `buildCommandLine includes collect-only and quiet flags`() {
        val args = PytestCollectorTask.buildPytestArgs("/path/to/collector.py")
        assertTrue("Should contain --collect-only", args.contains("--collect-only"))
        assertTrue("Should contain -q", args.contains("-q"))
        assertTrue("Should contain --no-header", args.contains("--no-header"))
    }

    @Test
    fun `buildCommandLine includes conftest collector plugin`() {
        val args = PytestCollectorTask.buildPytestArgs("/path/to/conftest_collector.py")
        val pIdx = args.indexOf("-p")
        assertTrue("Should contain -p flag", pIdx >= 0)
        assertEquals("Should reference conftest_collector", "conftest_collector", args[pIdx + 1])
    }

    @Test
    fun `buildCommandLine includes PYTHONPATH for collector script directory`() {
        val env = PytestCollectorTask.buildEnvironment("/path/to/conftest_collector.py")
        assertEquals("PYTHONPATH should point to collector script directory", "/path/to", env["PYTHONPATH"])
        assertEquals("Should disable bytecode writing", "1", env["PYTHONDONTWRITEBYTECODE"])
    }

    @Test
    fun `classifyExitCode - 0 is success`() {
        val result = PytestCollectorTask.classifyExitCode(0, "")
        assertTrue("Exit code 0 should be success", result.isEmpty())
    }

    @Test
    fun `classifyExitCode - 5 is no tests collected, not an error`() {
        val result = PytestCollectorTask.classifyExitCode(5, "")
        assertTrue("Exit code 5 (no tests) should not be an error", result.isEmpty())
    }

    @Test
    fun `classifyExitCode - 2 is usage error`() {
        val result = PytestCollectorTask.classifyExitCode(2, "some error output")
        assertEquals(1, result.size)
        assertTrue("Should mention exit code", result[0].contains("2"))
    }

    @Test
    fun `classifyExitCode - 1 is test failure, not a collection error`() {
        val result = PytestCollectorTask.classifyExitCode(1, "")
        assertTrue("Exit code 1 (test failures) should not be a collection error", result.isEmpty())
    }

    @Test
    fun `classifyExitCode - other codes are errors`() {
        val result = PytestCollectorTask.classifyExitCode(3, "internal error")
        assertEquals(1, result.size)
        assertTrue("Should contain stderr info", result[0].contains("internal error"))
    }

    @Test
    fun `classifyExitCode - minus 1 is error`() {
        val result = PytestCollectorTask.classifyExitCode(-1, "some failure")
        assertEquals(1, result.size)
        assertTrue("Should mention exit code -1", result[0].contains("-1"))
    }

    @Test
    fun `classifyExitCode - pytest not installed detected from stderr`() {
        val stderr = "ModuleNotFoundError: No module named 'pytest'"
        val result = PytestCollectorTask.classifyExitCode(1, stderr)
        assertEquals(1, result.size)
        assertTrue("Should mention pytest not installed", result[0].contains("not installed"))
    }

    @Test
    fun `classifyExitCode - No module named pytest detected`() {
        val stderr = "/usr/bin/python3: No module named pytest"
        val result = PytestCollectorTask.classifyExitCode(0, stderr)
        assertEquals(1, result.size)
        assertTrue("Should mention pytest not installed", result[0].contains("not installed"))
    }

    @Test
    fun `collectorScriptPath returns path to bundled script`() {
        val path = PytestCollectorTask.collectorScriptPath()
        assertNotNull("Should find collector script", path)
        assertTrue("Should end with conftest_collector.py", path!!.endsWith("conftest_collector.py"))
    }
}
