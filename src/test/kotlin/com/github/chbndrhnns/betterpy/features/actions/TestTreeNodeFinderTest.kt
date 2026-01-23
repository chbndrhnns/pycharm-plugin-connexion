package com.github.chbndrhnns.betterpy.features.actions

import com.github.chbndrhnns.betterpy.features.pytest.testtree.TestTreeNodeFinder
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import fixtures.TestBase
import javax.swing.tree.DefaultMutableTreeNode

class TestTreeNodeFinderTest : TestBase() {

    fun testFindsExactMatch() {
        val leaf = SMTestProxy("test_foo", false, null)
        val root = DefaultMutableTreeNode("root").apply {
            add(DefaultMutableTreeNode(leaf))
        }

        val path = TestTreeNodeFinder.findPath(
            root,
            nodeIdProvider = { proxy -> if (proxy === leaf) "a.py::test_foo" else null },
            targetNodeId = "a.py::test_foo"
        )

        assertNotNull(path)
        assertSame(leaf, (path!!.lastPathComponent as DefaultMutableTreeNode).userObject)
    }

    fun testFindsParametrizedByPrefix() {
        val leaf = SMTestProxy("test_foo[1]", false, null)
        val root = DefaultMutableTreeNode("root").apply {
            add(DefaultMutableTreeNode(leaf))
        }

        val path = TestTreeNodeFinder.findPath(
            root,
            nodeIdProvider = { proxy -> if (proxy === leaf) "a.py::test_foo[1]" else null },
            targetNodeId = "a.py::test_foo"
        )

        assertNotNull(path)
        assertSame(leaf, (path!!.lastPathComponent as DefaultMutableTreeNode).userObject)
    }

    fun testFindsSuiteNodeForClassTarget() {
        val suite = SMTestProxy("TestMyClass", true, null)
        val leaf = SMTestProxy("test_foo", false, null)

        val root = DefaultMutableTreeNode("root").apply {
            add(DefaultMutableTreeNode(suite).apply {
                add(DefaultMutableTreeNode(leaf))
            })
        }

        val path = TestTreeNodeFinder.findPath(
            root,
            nodeIdProvider = { proxy ->
                when (proxy) {
                    leaf -> "a.py::TestMyClass::test_foo"
                    // Simulate the common case where the suite node has no resolvable node id.
                    suite -> null
                    else -> null
                }
            },
            targetNodeId = "a.py::TestMyClass"
        )

        assertNotNull(path)
        assertSame(suite, (path!!.lastPathComponent as DefaultMutableTreeNode).userObject)
    }
}
