package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.ui.ModuleTreeNode
import com.github.chbndrhnns.betterpy.features.pytest.explorer.ui.ParametrizeTreeNode
import com.github.chbndrhnns.betterpy.features.pytest.explorer.ui.TestTreeNode
import com.github.chbndrhnns.betterpy.features.pytest.explorer.ui.TreeStatePreserver
import org.junit.Assert.*
import org.junit.Test
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class TreeStatePreserverTest {

    // --- collectAllKeys / hasRemovedNodes (pure logic, no JTree) ---

    @Test
    fun `collectAllKeys returns keys for all nodes`() {
        val root = node(
            "root",
            node(
                ModuleTreeNode("mod.py"),
                node(testNode("test_a")),
                node(testNode("test_b")),
            ),
        )
        val keys = TreeStatePreserver.collectAllKeys(root)
        // root + module + 2 tests = 4
        assertEquals(4, keys.size)
    }

    @Test
    fun `hasRemovedNodes returns false when new tree is superset`() {
        val old = setOf(listOf<Any>("r", "a"), listOf<Any>("r", "b"))
        val new = setOf(listOf<Any>("r", "a"), listOf<Any>("r", "b"), listOf<Any>("r", "c"))
        assertFalse(TreeStatePreserver.hasRemovedNodes(old, new))
    }

    @Test
    fun `hasRemovedNodes returns true when a node was removed`() {
        val old = setOf(listOf<Any>("r", "a"), listOf<Any>("r", "b"))
        val new = setOf(listOf<Any>("r", "a"))
        assertTrue(TreeStatePreserver.hasRemovedNodes(old, new))
    }

    @Test
    fun `hasRemovedNodes returns false for identical sets`() {
        val keys = setOf(listOf<Any>("r", "a"))
        assertFalse(TreeStatePreserver.hasRemovedNodes(keys, keys))
    }

    // --- JTree-based capture / restore ---

    @Test
    fun `expanded state is preserved when no nodes removed`() {
        val mod = ModuleTreeNode("tests/test_auth.py")
        val t1 = testNode("test_login")
        val t2 = testNode("test_logout")

        // Build initial tree and expand the module node
        val root1 = node("root", node(mod, node(t1), node(t2)))
        val tree = JTree(DefaultTreeModel(root1))
        expandAll(tree)
        val expanded = TreeStatePreserver.captureExpandedKeys(tree)
        assertTrue("Should have captured expanded keys", expanded.isNotEmpty())

        // Build a new tree with an extra test (superset — no removals)
        val t3 = testNode("test_signup")
        val root2 = node("root", node(mod, node(t1), node(t2), node(t3)))
        val oldKeys = TreeStatePreserver.collectAllKeys(root1)
        val newKeys = TreeStatePreserver.collectAllKeys(root2)
        assertFalse(TreeStatePreserver.hasRemovedNodes(oldKeys, newKeys))

        // Replace model and restore
        tree.model = DefaultTreeModel(root2)
        TreeStatePreserver.restoreExpandedState(tree, root2, expanded)

        // The module node should be expanded again
        val modulePath = TreePath(arrayOf(root2, root2.getChildAt(0)))
        assertTrue("Module node should be re-expanded", tree.isExpanded(modulePath))
    }

    @Test
    fun `expanded state is preserved even when nodes were removed`() {
        val mod = ModuleTreeNode("tests/test_auth.py")
        val t1 = testNode("test_login")
        val t2 = testNode("test_logout")

        val root1 = node("root", node(mod, node(t1), node(t2)))
        val tree = JTree(DefaultTreeModel(root1))
        expandAll(tree)
        val expanded = TreeStatePreserver.captureExpandedKeys(tree)

        // New tree removes test_logout
        val root2 = node("root", node(mod, node(t1)))

        // Replace model and restore — state should be preserved even with removals
        tree.model = DefaultTreeModel(root2)
        TreeStatePreserver.restoreExpandedState(tree, root2, expanded)

        val modulePath = TreePath(arrayOf(root2, root2.getChildAt(0)))
        assertTrue("Module node should still be expanded after removal", tree.isExpanded(modulePath))
    }

    @Test
    fun `selected state is restored after model replacement`() {
        val mod = ModuleTreeNode("tests/test_auth.py")
        val t1 = testNode("test_login")
        val t2 = testNode("test_logout")

        // Build a selected key directly via stableIdentity logic (captureSelectedKey
        // relies on JTree row expansion which is unreliable in headless mode).
        val selectedKey = TreeStatePreserver.collectAllKeys(
            node("root", node(mod, node(t1), node(t2)))
        ).first { key -> key.last().toString().contains("test_logout") }

        // Replace model (same structure) and restore selection
        val root2 = node("root", node(mod, node(t1), node(t2)))

        // IntelliJ's DefaultTreeUI requires EDT for selection changes
        SwingUtilities.invokeAndWait {
            val tree = JTree(DefaultTreeModel(root2))
            TreeStatePreserver.restoreSelectedState(tree, root2, selectedKey)

            val sel = tree.selectionPath
            assertNotNull("Selection should be restored", sel)
            val selNode = sel!!.lastPathComponent as DefaultMutableTreeNode
            assertEquals(t2, selNode.userObject)
        }
    }

    @Test
    fun `adding parametrize variant does not collapse tree`() {
        val mod = ModuleTreeNode("tests/test_math.py")
        val testWith2Params = CollectedTest(
            nodeId = "tests/test_math.py::test_add",
            modulePath = "tests/test_math.py",
            className = null,
            functionName = "test_add",
            fixtures = emptyList(),
            parametrizeIds = listOf("1-2-3", "4-5-9"),
        )
        val p1 = ParametrizeTreeNode("1-2-3", testWith2Params)
        val p2 = ParametrizeTreeNode("4-5-9", testWith2Params)

        val root1 = node("root", node(mod, node(TestTreeNode(testWith2Params), node(p1), node(p2))))
        val tree = JTree(DefaultTreeModel(root1))
        expandAll(tree)
        val expanded = TreeStatePreserver.captureExpandedKeys(tree)
        val oldKeys = TreeStatePreserver.collectAllKeys(root1)

        // New tree adds a third parametrize variant
        val testWith3Params = testWith2Params.copy(parametrizeIds = listOf("1-2-3", "4-5-9", "7-8-15"))
        val p3 = ParametrizeTreeNode("7-8-15", testWith3Params)
        val root2 = node(
            "root", node(
                mod, node(
                    TestTreeNode(testWith3Params),
                    node(ParametrizeTreeNode("1-2-3", testWith3Params)),
                    node(ParametrizeTreeNode("4-5-9", testWith3Params)),
                    node(p3),
                )
            )
        )
        val newKeys = TreeStatePreserver.collectAllKeys(root2)

        assertFalse(
            "Adding a param should NOT be detected as removal",
            TreeStatePreserver.hasRemovedNodes(oldKeys, newKeys)
        )

        tree.model = DefaultTreeModel(root2)
        TreeStatePreserver.restoreExpandedState(tree, root2, expanded)

        val modulePath = TreePath(arrayOf(root2, root2.getChildAt(0)))
        assertTrue("Module node should be re-expanded", tree.isExpanded(modulePath))
    }

    // --- helpers ---

    private fun testNode(name: String) = TestTreeNode(
        CollectedTest(
            nodeId = "tests/test_auth.py::$name",
            modulePath = "tests/test_auth.py",
            className = null,
            functionName = name,
            fixtures = emptyList(),
        )
    )

    private fun node(userObject: Any, vararg children: DefaultMutableTreeNode): DefaultMutableTreeNode {
        val n = DefaultMutableTreeNode(userObject)
        children.forEach { n.add(it) }
        return n
    }

    private fun expandAll(tree: JTree) {
        var i = 0
        while (i < tree.rowCount) {
            tree.expandRow(i)
            i++
        }
    }
}
