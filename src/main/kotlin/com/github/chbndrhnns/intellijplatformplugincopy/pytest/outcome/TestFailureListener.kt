package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.github.chbndrhnns.intellijplatformplugincopy.pytest.failedline.PyTestStackTraceParser
import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.stacktrace.DiffHyperlink
import com.intellij.openapi.project.Project

class TestFailureListener(private val project: Project) : SMTRunnerEventsListener {

    override fun onTestFailed(test: SMTestProxy) {
        updateFailedLine(test)
        val diffHyperlink: DiffHyperlink? = test.diffViewerProvider
        if (diffHyperlink != null) {
            val actual = diffHyperlink.right
            val expected = diffHyperlink.left

            val locationUrl = test.locationUrl
            if (locationUrl != null) {
                val key = PytestTestKeyFactory.fromTestProxy(locationUrl, test.metainfo)
                TestOutcomeDiffService.getInstance(project).put(key, OutcomeDiff(expected, actual))
            }
        }
    }

    private fun updateFailedLine(test: SMTestProxy) {
        val url = test.locationUrl ?: return
        val stacktrace = test.stacktrace ?: return
        val parser = PyTestStackTraceParser(stacktrace)
        val failedLine = parser.failedLine
        if (failedLine != -1) {
            val storage = TestStateStorage.getInstance(project)
            val record = storage.getState(url)
            if (record != null) {
                val updatedRecord = TestStateStorage.Record(
                    record.magnitude,
                    record.date,
                    record.configurationHash,
                    failedLine,
                    "",
                    record.errorMessage,
                    ""
                )
                storage.writeState(url, updatedRecord)
            }
        }
    }

    override fun onTestStarted(test: SMTestProxy) {
        val locationUrl = test.locationUrl
        if (locationUrl != null) {
            val key = PytestTestKeyFactory.fromTestProxy(locationUrl, test.metainfo)
            TestOutcomeDiffService.getInstance(project).remove(key)
        }
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
