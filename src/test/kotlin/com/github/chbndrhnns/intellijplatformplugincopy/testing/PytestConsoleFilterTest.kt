package com.github.chbndrhnns.intellijplatformplugincopy.testing

import fixtures.TestBase

class PytestConsoleFilterTest : TestBase() {

    fun testParse() {
        val filter = PytestConsoleFilter(project)
        val line = "test_file.py::test_func"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        assertEquals(line.length, item.highlightEndOffset)
    }

    fun testParseWithRelativePath() {
        val filter = PytestConsoleFilter(project)
        val line = "tests/test_file.py::test_func"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        assertEquals(line.length, item.highlightEndOffset)
    }

    fun testParseWithIndentation() {
        val filter = PytestConsoleFilter(project)
        val line = "  test_file.py::test_func"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(2, item.highlightStartOffset)
        assertEquals(line.length, item.highlightEndOffset)
    }

    fun testFalsePositives() {
        val filter = PytestConsoleFilter(project)

        // Not at start of line
        assertNull(filter.applyFilter("Note: test.py::test_func", 24))

        // Wrong extension
        assertNull(filter.applyFilter("test.txt::test_func", 19))

        // No node ID
        assertNull(filter.applyFilter("test.py::", 9))

        // Invalid separator
        assertNull(filter.applyFilter("test.py:test_func", 17))

        // Just the filename
        assertNull(filter.applyFilter("test.py", 7))
    }

    fun testParseWithStatus() {
        val filter = PytestConsoleFilter(project)
        val line = "test_file.py::test_func PASSED"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        // "test_file.py::test_func" is 23 chars long.
        // The match stops before the space.
        assertEquals(23, item.highlightEndOffset)
    }
}
