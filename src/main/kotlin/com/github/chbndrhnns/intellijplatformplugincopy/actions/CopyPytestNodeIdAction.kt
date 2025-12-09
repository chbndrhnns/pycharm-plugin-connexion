package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.testframework.TestTreeView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection
import javax.swing.tree.DefaultMutableTreeNode

class CopyPytestNodeIdAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
        val view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? TestTreeView
        e.presentation.isEnabledAndVisible = view != null && isPythonContext(view)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? TestTreeView ?: return
        val project = e.project ?: return

        val result = mutableListOf<String>()
        val selectionPaths = view.selectionPaths

        if (selectionPaths != null) {
            for (path in selectionPaths) {
                val component = path.lastPathComponent
                if (component is DefaultMutableTreeNode) {
                    collectNodeIds(component, result, project)
                }
            }
        }

        if (result.isNotEmpty()) {
            CopyPasteManager.getInstance().setContents(StringSelection(result.joinToString("\n")))
        }
    }

    private fun collectNodeIds(
        node: DefaultMutableTreeNode,
        result: MutableList<String>,
        project: Project
    ) {
        val proxy = TestProxyExtractor.getTestProxy(node)

        if (proxy != null && proxy.isLeaf) {
            val nodeId = PytestNodeIdGenerator.parseProxy(proxy, project)
            if (nodeId != null) {
                result.add(nodeId.nodeid)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i)
            if (child is DefaultMutableTreeNode) {
                collectNodeIds(child, result, project)
            }
        }
    }

    private fun isPythonContext(view: TestTreeView): Boolean {
        // Quick check: if the selected items are SMTestProxy, we assume it's supported
        // In a real scenario we'd check the run profile, but this is a decent heuristic
        val selectionPaths = view.selectionPaths
        if (selectionPaths != null && selectionPaths.isNotEmpty()) {
            val component = selectionPaths[0].lastPathComponent
            if (component is DefaultMutableTreeNode && TestProxyExtractor.getTestProxy(component) != null) {
                return true
            }
        }
        return false
    }
}
