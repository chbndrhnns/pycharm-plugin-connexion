package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

object TestTreeNodeFinder {

    fun findPath(
        root: DefaultMutableTreeNode,
        nodeIdProvider: (SMTestProxy) -> String?,
        targetNodeId: String
    ): TreePath? {
        val path = ArrayList<Any>()
        return findPathInternal(root, nodeIdProvider, targetNodeId, path)
    }

    private fun findPathInternal(
        node: DefaultMutableTreeNode,
        nodeIdProvider: (SMTestProxy) -> String?,
        targetNodeId: String,
        currentPath: MutableList<Any>
    ): TreePath? {
        currentPath.add(node)

        val proxy = TestProxyExtractor.getTestProxy(node)
        if (proxy != null && proxy.isLeaf) {
            val nodeId = nodeIdProvider(proxy)
            if (!nodeId.isNullOrBlank() && matchesTarget(nodeId, targetNodeId)) {
                return TreePath(currentPath.toTypedArray())
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val found = findPathInternal(child, nodeIdProvider, targetNodeId, currentPath)
            if (found != null) return found
        }

        currentPath.removeAt(currentPath.lastIndex)
        return null
    }

    private fun matchesTarget(candidateNodeId: String, targetNodeId: String): Boolean {
        if (candidateNodeId == targetNodeId) return true

        // Parametrized tests in the test tree often have a suffix like: test_foo[param]
        // The editor-based node id cannot know the exact parameter set, so allow matching by prefix.
        if (candidateNodeId.startsWith(targetNodeId)) {
            val next = candidateNodeId.getOrNull(targetNodeId.length)
            if (next == '[') return true
        }

        return false
    }
}
