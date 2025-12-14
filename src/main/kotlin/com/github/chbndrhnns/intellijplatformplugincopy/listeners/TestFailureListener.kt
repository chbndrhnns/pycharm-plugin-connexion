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
            if (locationUrl != null && actual != null && expected != null) {
                TestFailureState.getInstance(project).setDiffData(
                    locationUrl,
                    DiffData(expected, actual)
                )
            }
        }
    }

    override fun onTestStarted(test: SMTestProxy) {
        val locationUrl = test.locationUrl
        if (locationUrl != null) {
            TestFailureState.getInstance(project).clearDiffData(locationUrl)
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
