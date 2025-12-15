package com.github.chbndrhnns.intellijplatformplugincopy.listeners

import com.github.chbndrhnns.intellijplatformplugincopy.services.DiffData
import com.github.chbndrhnns.intellijplatformplugincopy.services.TestFailureState
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.stacktrace.DiffHyperlink
import com.intellij.openapi.project.Project

class TestFailureListener(private val project: Project) : SMTRunnerEventsListener {

    override fun onTestFailed(test: SMTestProxy) {
        val diffHyperlink: DiffHyperlink? = test.diffViewerProvider
        if (diffHyperlink != null) {
            val actual = diffHyperlink.right
            val expected = diffHyperlink.left

            val locationUrl = test.locationUrl
            if (locationUrl != null) {
                val key = buildTestKey(locationUrl, test.metainfo)
                TestFailureState.getInstance(project).setDiffData(
                    key,
                    DiffData(expected, actual)
                )
            }
        }
    }

    override fun onTestStarted(test: SMTestProxy) {
        val locationUrl = test.locationUrl
        if (locationUrl != null) {
            val key = buildTestKey(locationUrl, test.metainfo)
            TestFailureState.getInstance(project).clearDiffData(key)
        }
    }

    /**
     * Builds a test key by combining locationUrl with metainfo for parametrized tests.
     * For parametrized tests, metainfo contains the parameter values (e.g., "test_str[abc-defg]").
     * We extract the bracketed part and append it to the locationUrl.
     */
    private fun buildTestKey(locationUrl: String, metainfo: String?): String {
        if (metainfo.isNullOrEmpty()) {
            return locationUrl
        }
        
        // Extract parameter values from metainfo (e.g., "test_str[abc-defg]" -> "[abc-defg]")
        val bracketStart = metainfo.indexOf('[')
        if (bracketStart != -1) {
            val paramPart = metainfo.substring(bracketStart)
            return locationUrl + paramPart
        }
        
        return locationUrl
    }

    override fun onTestingStarted(testsRoot: SMTestProxy.SMRootTestProxy) {}
    override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) {}
    override fun onTestsCountInSuite(count: Int) {}
    override fun onTestFinished(test: SMTestProxy) {}
    override fun onTestIgnored(test: SMTestProxy) {}
    override fun onSuiteFinished(suite: SMTestProxy) {}
    override fun onSuiteStarted(suite: SMTestProxy) {}
    override fun onCustomProgressTestsCategory(categoryName: String?, testCount: Int) {}
    override fun onCustomProgressTestStarted() {}
    override fun onCustomProgressTestFailed() {}
    override fun onCustomProgressTestFinished() {}
    override fun onSuiteTreeNodeAdded(testProxy: SMTestProxy) {}
    override fun onSuiteTreeStarted(suite: SMTestProxy) {}
}
