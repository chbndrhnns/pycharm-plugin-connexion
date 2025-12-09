package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.testframework.TestTreeView
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection
import javax.swing.tree.DefaultMutableTreeNode

class CopyFQNAction : AnAction() {

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
                    collectFQNs(component, result, project)
                }
            }
        }

        if (result.isNotEmpty()) {
            val uniqueResult = result.distinct().sorted()
            CopyPasteManager.getInstance().setContents(StringSelection(uniqueResult.joinToString("\n")))
        }
    }

    internal fun collectFQNs(
        node: DefaultMutableTreeNode,
        result: MutableList<String>,
        project: Project
    ) {
        val proxy = TestProxyExtractor.getTestProxy(node)

        if (proxy != null) {
            collectFQNsFromProxy(proxy, result, project)
        } else {
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i)
                if (child is DefaultMutableTreeNode) {
                    collectFQNs(child, result, project)
                }
            }
        }
    }

    private fun collectFQNsFromProxy(
        proxy: SMTestProxy,
        result: MutableList<String>,
        project: Project
    ) {
        if (proxy.isLeaf) {
            val fqn = generateFQN(proxy, project)
            if (fqn != null) {
                result.add(fqn)
            }
        } else {
            for (child in proxy.children) {
                collectFQNsFromProxy(child, result, project)
            }
        }
    }

    internal fun generateFQN(proxy: SMTestProxy, project: Project): String? {
        val url = proxy.locationUrl
        if (url != null && url.startsWith("python_uttestid://")) {
            return stripParameters(url.removePrefix("python_uttestid://"))
        }

        val nodeId = PytestNodeIdGenerator.parseProxy(proxy, project) ?: return null
        // Transform "path/to/file.py::Class::method" to "path.to.file.Class.method"
        val fqn = nodeId.nodeid.replace(".py", "")
            .replace("/", ".")
            .replace("::", ".")
        return stripParameters(fqn)
    }

    private fun stripParameters(fqn: String): String {
        if (fqn.endsWith("]")) {
            val index = fqn.lastIndexOf('[')
            if (index != -1) {
                return fqn.substring(0, index)
            }
        }
        return fqn
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
