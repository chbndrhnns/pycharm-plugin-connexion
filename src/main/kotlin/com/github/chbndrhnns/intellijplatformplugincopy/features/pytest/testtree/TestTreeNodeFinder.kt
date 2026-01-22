package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.testtree

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
        val targetKind = targetKind(targetNodeId)
        return findPathInternal(root, nodeIdProvider, targetNodeId, targetKind, path)
    }

    private fun findPathInternal(
        node: DefaultMutableTreeNode,
        nodeIdProvider: (SMTestProxy) -> String?,
        targetNodeId: String,
        targetKind: TargetKind,
        currentPath: MutableList<Any>
    ): TreePath? {
        currentPath.add(node)

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val found = findPathInternal(child, nodeIdProvider, targetNodeId, targetKind, currentPath)
            if (found != null) return found
        }

        val proxy = TestProxyExtractor.getTestProxy(node)
        if (proxy != null) {
            val nodeId = nodeIdProvider(proxy)

            // Exact match is always preferred.
            if (!nodeId.isNullOrBlank() && nodeId == targetNodeId) {
                return TreePath(currentPath.toTypedArray())
            }

            when (targetKind) {
                TargetKind.LEAF -> {
                    if (!nodeId.isNullOrBlank() && matchesLeafTarget(nodeId, targetNodeId)) {
                        return TreePath(currentPath.toTypedArray())
                    }
                }

                TargetKind.CONTAINER -> {
                    // When caret is on a class/container, prefer selecting the suite node.
                    // Some suite proxies don't carry enough location data for an exact node id,
                    // so fall back to "has matching descendant leaf".
                    if (proxy.isSuite && containsMatchingLeaf(node, nodeIdProvider, targetNodeId)) {
                        return TreePath(currentPath.toTypedArray())
                    }
                }
            }
        }

        currentPath.removeAt(currentPath.lastIndex)
        return null
    }

    private enum class TargetKind {
        LEAF,
        CONTAINER,
    }

    private fun targetKind(targetNodeId: String): TargetKind {
        if (targetNodeId.endsWith(']')) return TargetKind.LEAF

        val last = targetNodeId.substringAfterLast("::")
        // Heuristic: test functions/methods are leaves; test classes/modules are containers.
        if (last.startsWith("test_")) return TargetKind.LEAF

        return TargetKind.CONTAINER
    }

    private fun matchesLeafTarget(candidateNodeId: String, targetNodeId: String): Boolean {
        // Exact match is handled earlier.

        // Parametrized tests in the test tree often have a suffix like: test_foo[param]
        // When the editor-based node id cannot know the exact parameter set, allow matching by prefix.
        if (candidateNodeId.startsWith(targetNodeId)) {
            val next = candidateNodeId.getOrNull(targetNodeId.length)
            if (next == '[') return true
        }

        return false
    }

    private fun containsMatchingLeaf(
        node: DefaultMutableTreeNode,
        nodeIdProvider: (SMTestProxy) -> String?,
        targetNodeId: String,
    ): Boolean {
        val proxy = TestProxyExtractor.getTestProxy(node)
        if (proxy != null && proxy.isLeaf) {
            val nodeId = nodeIdProvider(proxy)
            if (!nodeId.isNullOrBlank() && nodeId.startsWith(targetNodeId)) {
                val next = nodeId.getOrNull(targetNodeId.length)
                if (next == ':' || next == '[') return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            if (containsMatchingLeaf(child, nodeIdProvider, targetNodeId)) return true
        }

        return false
    }
}
