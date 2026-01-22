package com.github.chbndrhnns.intellijplatformplugincopy.features.testing

import fixtures.TestBase

class PythonClassConsoleFilterTest : TestBase() {

    fun testParseSingleQuotes() {
        val filter = PythonClassConsoleFilter(project)
        val line = "<class 'src.adapters.outbound.my_adapter.HttpAdapter'>"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        assertEquals(line.length, item.highlightEndOffset)
    }

    fun testParseDoubleQuotes() {
        val filter = PythonClassConsoleFilter(project)
        val line = "<class \"src.adapters.outbound.my_adapter.HttpAdapter\">"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        assertEquals(line.length, item.highlightEndOffset)
    }

    fun testParseEmbeddedInText() {
        val filter = PythonClassConsoleFilter(project)
        val prefix = "Some text before "
        val classRef = "<class 'src.MyClass'>"
        val suffix = " and after."
        val line = prefix + classRef + suffix
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(prefix.length, item.highlightStartOffset)
        assertEquals(prefix.length + classRef.length, item.highlightEndOffset)
    }

    fun testNoMatch() {
        val filter = PythonClassConsoleFilter(project)
        val line = "Just some text without class ref"
        val result = filter.applyFilter(line, line.length)
        assertNull(result)
    }
}
