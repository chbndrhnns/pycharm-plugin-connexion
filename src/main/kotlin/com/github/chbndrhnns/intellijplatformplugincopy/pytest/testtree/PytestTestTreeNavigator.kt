package com.github.chbndrhnns.intellijplatformplugincopy.pytest.testtree

import com.intellij.execution.testframework.TestTreeView
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.RunContentManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Component
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode

object PytestTestTreeNavigator {

    fun selectAndReveal(project: Project, targetNodeId: String): Boolean {
        val view = findActiveTestTreeView(project) ?: run {
            notify(project, "No active test tree found (select a Run toolwindow tab with test results).")
            return false
        }

        val root = view.model.root as? DefaultMutableTreeNode ?: return false
        val path = TestTreeNodeFinder.findPath(root, { proxy -> nodeIdForProxy(project, proxy) }, targetNodeId)
            ?: run {
                notify(project, "No matching test node found in the active test tree.")
                return false
            }

        view.selectionPath = path
        view.scrollPathToVisible(path)

        ToolWindowManager.getInstance(project).getToolWindow("Run")?.activate(null)
        return true
    }

    private fun nodeIdForProxy(project: Project, proxy: SMTestProxy): String? {
        return PytestNodeIdGenerator.parseProxy(proxy, project)?.nodeid
    }

    private fun findActiveTestTreeView(project: Project): TestTreeView? {
        val content = RunContentManager.getInstance(project).selectedContent ?: return null
        val component = content.component
        return findComponent(component) as? TestTreeView
    }

    private fun findComponent(component: Component?): Component? {
        if (component == null) return null
        if (component is TestTreeView) return component
        if (component !is JComponent) return null
        for (child in component.components) {
            val found = findComponent(child)
            if (found != null) return found
        }
        return null
    }

    private fun notify(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Python DDD Toolkit")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }
}
