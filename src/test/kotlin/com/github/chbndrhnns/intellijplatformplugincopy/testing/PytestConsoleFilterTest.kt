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

    fun testParseWithColorizedStatus() {
        val filter = PytestConsoleFilter(project)
        val line = "test_file.py::test_func \u001B[32mPASSED\u001B[0m"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        assertEquals("test_file.py::test_func".length, item.highlightEndOffset)
    }

    fun testParseWithQuotedNodeId() {
        val filter = PytestConsoleFilter(project)
        val line = "tests/test_.py::test_this[<class 'src.MyClass'>]"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        assertEquals(line.length, item.highlightEndOffset)
    }

    fun testParseStopsAfterClosingBracketBeforeProgress() {
        val filter = PytestConsoleFilter(project)
        val line =
            " tests/unit/test_integrity.py::test_no_skip_markers_on_adapter_fixture[<class 'src.adapters.outbound._openstack._os_network_repo.HttpOpenstackNetworkRepository'>] ✓                                                                                                            48% ████▉ "
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(1, item.highlightStartOffset)
        val expectedEnd = line.indexOf(']') + 1
        assertEquals(expectedEnd, item.highlightEndOffset)
    }

    fun testParseWithNestedBracketsInsideParametrization() {
        val filter = PytestConsoleFilter(project)
        val line =
            "tests/unit/test_integrity.py::test_integration_test_marker[tests/integration/adapters/outbound/openstack/test_os_network_repo.py::TestCreate::test_returns_existing_networks_in_error[FakeOpenstackNetworkRepository]]"

        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        // The node id legitimately ends with "]]" here (nested parametrization).
        assertEquals(line.length, item.highlightEndOffset)
    }

    fun testParseWithFailedPrefixAndDashSuffix() {
        val filter = PytestConsoleFilter(project)
        val line =
            "FAILED tests/unit/test_something.py::TestCreate::test_404_if_not_found - AttributeError: 'TestCreate' object has no attribute 'post'"

        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()

        val expectedStart = line.indexOf("tests/")
        assertEquals(expectedStart, item.highlightStartOffset)
        val expectedEnd = line.indexOf(" - ")
        assertEquals(expectedEnd, item.highlightEndOffset)
    }

    fun testParseShortTestSummaryInfo() {
        val filter = PytestConsoleFilter(project)
        val line = "FAILED tests/mytest.py - AttributeError: module 'src.abc' has no attribute 'MyAttr'"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()

        val expectedStart = line.indexOf("tests/")
        assertEquals(expectedStart, item.highlightStartOffset)
        val expectedEnd = line.indexOf(" - ")
        assertEquals(expectedEnd, item.highlightEndOffset)
    }
}
