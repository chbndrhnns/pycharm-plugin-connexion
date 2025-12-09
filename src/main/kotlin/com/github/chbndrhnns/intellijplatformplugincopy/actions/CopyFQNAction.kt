package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.testframework.TestTreeView
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection
import javax.swing.tree.DefaultMutableTreeNode

class CopyFQNAction : AnAction() {

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
                    collectFQNs(component, result, project)
                }
            }
        }

        if (result.isNotEmpty()) {
            CopyPasteManager.getInstance().setContents(StringSelection(result.joinToString("\n")))
        }
    }

    private fun collectFQNs(
        node: DefaultMutableTreeNode,
        result: MutableList<String>,
        project: Project
    ) {
        val proxy = TestProxyExtractor.getTestProxy(node)

        if (proxy != null && proxy.isLeaf) {
            val fqn = generateFQN(proxy, project)
            if (fqn != null) {
                result.add(fqn)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i)
            if (child is DefaultMutableTreeNode) {
                collectFQNs(child, result, project)
            }
        }
    }

    private fun generateFQN(proxy: SMTestProxy, project: Project): String? {
        val url = proxy.locationUrl
        if (url != null && url.startsWith("python_uttestid://")) {
            return url.removePrefix("python_uttestid://")
        }

        val nodeId = PytestNodeIdGenerator.parseProxy(proxy, project) ?: return null
        // Transform "path/to/file.py::Class::method" to "path.to.file.Class.method"
        return nodeId.nodeid.replace(".py", "")
            .replace("/", ".")
            .replace("::", ".")
    }

    private fun isPythonContext(view: TestTreeView): Boolean {
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
