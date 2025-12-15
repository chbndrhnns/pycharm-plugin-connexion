package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import fixtures.TestBase

class TestFailureListenerTest : TestBase() {

    fun `test stores failure with raw key from complex location url`() {
        val listener = TestFailureListener(project)
        // Use the format described in the issue
        val complexUrl = "python</Users/jo/PyCharmMiscProject/tests>://test_fail.test_"
        val testProxy = SMTestProxy("test_", false, complexUrl)

        // Simulate diff data
        // expected, actual
        testProxy.setTestComparisonFailed("msg", "stack", "actual_val", "expected_val")

        listener.onTestFailed(testProxy)

        val state = TestOutcomeDiffService.getInstance(project)

        // The Intention should now use the raw key
        val expectedKey = complexUrl

        val data = state.get(expectedKey)
        assertNotNull(
            "Should find data with raw key '$expectedKey' but found null. State keys: ${state.getAllKeys()}",
            data
        )
        assertEquals("actual_val", data?.actual)
        assertEquals("expected_val", data?.expected)
    }

    fun `test clears failure with raw key`() {
        val listener = TestFailureListener(project)
        val complexUrl = "python</Users/jo/PyCharmMiscProject/tests>://test_fail.test_"
        val testProxy = SMTestProxy("test_", false, complexUrl)

        // Pre-fill state
        val state = TestOutcomeDiffService.getInstance(project)
        val expectedKey = complexUrl
        state.put(expectedKey, OutcomeDiff("exp", "act"))

        // Simulate test start
        listener.onTestStarted(testProxy)

        assertNull("Should have cleared data for key '$expectedKey'", state.get(expectedKey))
    }

    fun `test stores failure with metainfo for parametrized test`() {
        val listener = TestFailureListener(project)
        // Simulate a parametrized test as described in the issue
        // locationUrl="python</Users/cleancoder/PyCharmMiscProject>://tests.test_param.test_str."
        // metainfo="test_str[abc-defg]"
        val locationUrl = "python</Users/cleancoder/PyCharmMiscProject>://tests.test_param.test_str"
        val metainfo = "test_str[abc-defg]"
        val testProxy = SMTestProxy("test_str", false, locationUrl)
        testProxy.metainfo = metainfo

        // Simulate diff data
        testProxy.setTestComparisonFailed("msg", "stack", "abc", "defg")

        listener.onTestFailed(testProxy)

        val state = TestOutcomeDiffService.getInstance(project)

        // The key should be locationUrl + "[" + parameter values from metainfo + "]"
        // Expected: "python</Users/cleancoder/PyCharmMiscProject>://tests.test_param.test_str[abc-defg]"
        val expectedKey = "$locationUrl[abc-defg]"

        val data = state.get(expectedKey)
        assertNotNull(
            "Should find data with parametrized key '$expectedKey' but found null. State keys: ${state.getAllKeys()}",
            data
        )
        assertEquals("abc", data?.actual)
        assertEquals("defg", data?.expected)
    }

    fun `test clears failure with metainfo for parametrized test`() {
        val listener = TestFailureListener(project)
        val locationUrl = "python</Users/cleancoder/PyCharmMiscProject>://tests.test_param.test_str"
        val metainfo = "test_str[abc-defg]"
        val testProxy = SMTestProxy("test_str", false, locationUrl)
        testProxy.metainfo = metainfo

        // Pre-fill state
        val state = TestOutcomeDiffService.getInstance(project)
        val expectedKey = "$locationUrl[abc-defg]"
        state.put(expectedKey, OutcomeDiff("exp", "act"))

        // Simulate test start
        listener.onTestStarted(testProxy)

        assertNull("Should have cleared data for parametrized key '$expectedKey'", state.get(expectedKey))
    }
}
