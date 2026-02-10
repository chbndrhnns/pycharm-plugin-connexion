package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Captures and restores JTree expanded/selected state across model replacements.
 *
 * Expanded paths are identified by the [userObject] identity chain from root to node.
 * If any previously-existing node was removed in the new tree, the tree is left collapsed
 * (fresh render) so the user notices the structural change.
 */
internal object TreeStatePreserver {

    /**
     * Extracts a stable identity from a tree node's [userObject].
     * For [TestTreeNode], uses only the function name (ignoring parametrizeIds which change when params are added/removed).
     * For [ParametrizeTreeNode], uses only the parametrizeId string.
     * For all other types, uses the userObject directly (relies on data class equality).
     */
    private fun stableIdentity(userObject: Any): Any = when (userObject) {
        is TestTreeNode -> "test:${userObject.test.modulePath}::${userObject.test.className.orEmpty()}::${userObject.test.functionName}"
        is ParametrizeTreeNode -> "param:${userObject.parametrizeId}"
        else -> userObject
    }

    /** Opaque key list representing a path from root to a node via stable identities. */
    private fun pathKey(path: TreePath): List<Any> =
        (0 until path.pathCount).map { stableIdentity((path.getPathComponent(it) as DefaultMutableTreeNode).userObject) }

    /** Collect the set of [userObject]-based path keys for every expanded row. */
    fun captureExpandedKeys(tree: JTree): Set<List<Any>> {
        val keys = mutableSetOf<List<Any>>()
        for (i in 0 until tree.rowCount) {
            val path = tree.getPathForRow(i) ?: continue
            if (tree.isExpanded(path)) {
                keys.add(pathKey(path))
            }
        }
        return keys
    }

    /** Capture the selected node's path key (if any). */
    fun captureSelectedKey(tree: JTree): List<Any>? {
        val sel = tree.selectionPath ?: return null
        return pathKey(sel)
    }

    /**
     * Collect every [userObject]-based path key present in [root] (including leaves).
     */
    fun collectAllKeys(root: DefaultMutableTreeNode): Set<List<Any>> {
        val result = mutableSetOf<List<Any>>()
        fun walk(node: DefaultMutableTreeNode, prefix: List<Any>) {
            val userObj = node.userObject ?: return
            val key = prefix + stableIdentity(userObj)
            result.add(key)
            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i) as DefaultMutableTreeNode, key)
            }
        }
        walk(root, emptyList())
        return result
    }

    /**
     * Returns `true` when [oldKeys] contains at least one key that is absent from [newKeys],
     * meaning a node was removed.
     */
    fun hasRemovedNodes(oldKeys: Set<List<Any>>, newKeys: Set<List<Any>>): Boolean =
        !newKeys.containsAll(oldKeys)

    /**
     * Re-expand previously expanded paths in [tree] whose root is [root].
     * Only paths whose key exists in [expandedKeys] are expanded.
     */
    fun restoreExpandedState(tree: JTree, root: DefaultMutableTreeNode, expandedKeys: Set<List<Any>>) {
        fun walk(node: DefaultMutableTreeNode, prefix: List<Any>) {
            val key = prefix + stableIdentity(node.userObject)
            if (key in expandedKeys) {
                tree.expandPath(treePath(node))
            }
            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i) as DefaultMutableTreeNode, key)
            }
        }
        walk(root, emptyList())
    }

    /**
     * Re-select the node matching [selectedKey] if it still exists.
     */
    fun restoreSelectedState(tree: JTree, root: DefaultMutableTreeNode, selectedKey: List<Any>) {
        fun walk(node: DefaultMutableTreeNode, prefix: List<Any>): TreePath? {
            val key = prefix + stableIdentity(node.userObject)
            if (key == selectedKey) return treePath(node)
            for (i in 0 until node.childCount) {
                val found = walk(node.getChildAt(i) as DefaultMutableTreeNode, key)
                if (found != null) return found
            }
            return null
        }

        val path = walk(root, emptyList())
        if (path != null) {
            tree.selectionPath = path
        }
    }

    private fun treePath(node: DefaultMutableTreeNode): TreePath {
        val nodes = mutableListOf<Any>()
        var current: DefaultMutableTreeNode? = node
        while (current != null) {
            nodes.add(0, current)
            current = current.parent as? DefaultMutableTreeNode
        }
        return TreePath(nodes.toTypedArray())
    }
}
