package com.github.chbndrhnns.intellijplatformplugincopy.pytest.testtree

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

abstract class AbstractCopyTestNodeAction(
    private val separator: String
) : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
        val view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? TestTreeView
        e.presentation.isEnabledAndVisible = view != null
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
                    collect(component, result, project)
                }
            }
        }

        val finalResult = processResult(result)

        if (finalResult.isNotEmpty()) {
            CopyPasteManager.getInstance().setContents(StringSelection(finalResult.joinToString(separator)))
        }
    }

    internal fun collect(node: DefaultMutableTreeNode, result: MutableList<String>, project: Project) {
        val proxy = TestProxyExtractor.getTestProxy(node)
        if (proxy != null && proxy.isLeaf) {
            val text = getValuableText(proxy, project)
            if (!text.isNullOrBlank()) {
                result.add(text)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i)
            if (child is DefaultMutableTreeNode) {
                collect(child, result, project)
            }
        }
    }

    protected abstract fun getValuableText(proxy: SMTestProxy, project: Project): String?

    protected open fun processResult(result: List<String>): List<String> = result
}
