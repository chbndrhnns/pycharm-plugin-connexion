package com.github.chbndrhnns.intellijplatformplugincopy.listeners

import com.github.chbndrhnns.intellijplatformplugincopy.services.TestFailureState
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
        
        val state = TestFailureState.getInstance(project)
        
        // The Intention should now use the raw key
        val expectedKey = complexUrl
        
        val data = state.getDiffData(expectedKey)
        assertNotNull("Should find data with raw key '$expectedKey' but found null. State keys: ${state.getAllKeys()}", data)
        assertEquals("actual_val", data?.actual)
        assertEquals("expected_val", data?.expected)
    }
    
    fun `test clears failure with raw key`() {
        val listener = TestFailureListener(project)
        val complexUrl = "python</Users/jo/PyCharmMiscProject/tests>://test_fail.test_"
        val testProxy = SMTestProxy("test_", false, complexUrl)
        
        // Pre-fill state
        val state = TestFailureState.getInstance(project)
        val expectedKey = complexUrl
        state.setDiffData(expectedKey, com.github.chbndrhnns.intellijplatformplugincopy.services.DiffData("exp", "act"))
        
        // Simulate test start
        listener.onTestStarted(testProxy)
        
        assertNull("Should have cleared data for key '$expectedKey'", state.getDiffData(expectedKey))
    }
}
