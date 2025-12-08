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
        assertEquals(0, item.highlightStartOffset)
        assertEquals(line.length, item.highlightEndOffset)
    }
}
