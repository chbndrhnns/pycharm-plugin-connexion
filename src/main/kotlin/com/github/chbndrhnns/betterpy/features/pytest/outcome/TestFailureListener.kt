package com.github.chbndrhnns.betterpy.features.pytest.outcome

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.stacktrace.DiffHyperlink
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class TestFailureListener(private val project: Project) : SMTRunnerEventsListener {

    override fun onTestFailed(test: SMTestProxy) {
        LOG.debug("TestFailureListener.onTestFailed: processing test '${test.name}', locationUrl='${test.locationUrl}'")

        val locationUrl = test.locationUrl
        if (locationUrl == null) {
            LOG.debug("TestFailureListener.onTestFailed: locationUrl is null, skipping storage")
            return
        }

        val stacktrace = test.stacktrace
        LOG.debug("TestFailureListener.onTestFailed: stacktrace available=${stacktrace != null}, length=${stacktrace?.length ?: 0}")

        val failedLine = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)
        LOG.debug("TestFailureListener.onTestFailed: parsed failed line number: $failedLine")

        val diffHyperlink: DiffHyperlink? = test.diffViewerProvider
        val expected: String
        val actual: String

        if (diffHyperlink != null) {
            expected = diffHyperlink.left
            actual = diffHyperlink.right
            LOG.debug("TestFailureListener.onTestFailed: found diff - expected length=${expected.length}, actual length=${actual.length}")
        } else {
            // For non-assertion errors (RuntimeError, ValueError, etc.), store empty strings
            expected = ""
            actual = ""
            LOG.debug("TestFailureListener.onTestFailed: no diffHyperlink found (non-assertion error), using empty expected/actual")
        }

        val key = PytestTestKeyFactory.fromTestProxy(locationUrl, test.metainfo)
        val diff = OutcomeDiff(expected, actual, failedLine)
        LOG.debug("TestFailureListener.onTestFailed: storing OutcomeDiff with key='$key', failedLine=$failedLine")

        TestOutcomeDiffService.getInstance(project).put(key, diff)
    }

    companion object {
        private val LOG = Logger.getInstance(TestFailureListener::class.java)
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
