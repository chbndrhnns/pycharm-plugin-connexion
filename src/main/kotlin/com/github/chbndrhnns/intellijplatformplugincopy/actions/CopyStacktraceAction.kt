package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.execution.testframework.TestTreeView
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import javax.swing.tree.DefaultMutableTreeNode

class CopyStacktraceAction : AbstractCopyTestNodeAction("\n\n") {

    override fun update(e: AnActionEvent) {
        if (!PluginSettingsState.instance().state.enableCopyStacktraceAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? TestTreeView
        if (view == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabledAndVisible = hasSelectedFailedTestsWithStacktrace(view)
    }

    private fun hasSelectedFailedTestsWithStacktrace(view: TestTreeView): Boolean {
        val selectionPaths = view.selectionPaths ?: return false

        for (path in selectionPaths) {
            val component = path.lastPathComponent
            if (component is DefaultMutableTreeNode && hasFailedLeafWithStacktrace(component)) {
                return true
            }
        }

        return false
    }

    private fun hasFailedLeafWithStacktrace(node: DefaultMutableTreeNode): Boolean {
        val proxy = TestProxyExtractor.getTestProxy(node)
        if (proxy != null && proxy.isLeaf && proxy.isDefect && !proxy.stacktrace.isNullOrBlank()) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i)
            if (child is DefaultMutableTreeNode && hasFailedLeafWithStacktrace(child)) {
                return true
            }
        }

        return false
    }

    override fun getValuableText(proxy: SMTestProxy, project: Project): String? {
        if (proxy.isDefect) {
            return proxy.stacktrace
        }
        return null
    }
}
