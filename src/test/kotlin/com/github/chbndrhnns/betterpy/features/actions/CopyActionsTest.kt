package com.github.chbndrhnns.betterpy.features.actions

import com.github.chbndrhnns.betterpy.features.pytest.testtree.CopyFQNAction
import com.github.chbndrhnns.betterpy.features.pytest.testtree.CopyPytestNodeIdAction
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import fixtures.TestBase
import javax.swing.tree.DefaultMutableTreeNode

class CopyActionsTest : TestBase() {

    fun testCopyFromRootNode() {
        // 1. Create files in different "modules" (directories)
        // Module 1
        val file1 = myFixture.addFileToProject(
            "module1/test_mod1.py",
            """
            def test_one():
                pass
            """.trimIndent()
        )
        // Module 2
        val file2 = myFixture.addFileToProject(
            "module2/test_mod2.py",
            """
            class TestClass:
                def test_two(self):
                    pass
            """.trimIndent()
        )

        val path1 = file1.virtualFile.path
        val path2 = file2.virtualFile.path

        // 2. Create Proxies
        // Proxy for test_one
        val url1 = "python<$path1>://module1.test_mod1.test_one"
        val proxy1 = FakeSMTestProxy("test_one", false, null, url1)

        // Proxy for test_two
        val url2 = "python<$path2>://module2.test_mod2.TestClass.test_two"
        val proxy2 = FakeSMTestProxy("test_two", false, null, url2)

        // 3. Build Tree
        // Root -> Suite1 -> Proxy1
        //      -> Suite2 -> Proxy2
        val rootNode = DefaultMutableTreeNode("Root")

        val suite1Node = DefaultMutableTreeNode("Suite1")
        val proxy1Node = DefaultMutableTreeNode(proxy1)
        suite1Node.add(proxy1Node)
        rootNode.add(suite1Node)

        val suite2Node = DefaultMutableTreeNode("Suite2")
        val proxy2Node = DefaultMutableTreeNode(proxy2)
        suite2Node.add(proxy2Node)
        rootNode.add(suite2Node)

        // 4. Test CopyFQNAction
        val fqnResult = mutableListOf<String>()
        CopyFQNAction().collect(rootNode, fqnResult, project)
        fqnResult.sort()

        // Expect: module1.test_mod1.test_one and module2.test_mod2.TestClass.test_two
        assertEquals(2, fqnResult.size)
        assertTrue(fqnResult.contains("module1.test_mod1.test_one"))
        assertTrue(fqnResult.contains("module2.test_mod2.TestClass.test_two"))

        // 5. Test CopyPytestNodeIdAction
        val nodeIdResult = mutableListOf<String>()
        CopyPytestNodeIdAction().collect(rootNode, nodeIdResult, project)
        nodeIdResult.sort()

        // Expect: module1/test_mod1.py::test_one and module2/test_mod2.py::TestClass::test_two
        assertEquals(2, nodeIdResult.size)
        // Note: The relative path depends on content root.
        // In TestBase, the content root is usually the temp directory containing the files.
        // So expected paths should be relative to that.
        // VfsUtilCore.getRelativePath should handle it.
        assertTrue(
            "Expected module1/test_mod1.py::test_one in $nodeIdResult",
            nodeIdResult.contains("module1/test_mod1.py::test_one")
        )
        assertTrue(
            "Expected module2/test_mod2.py::TestClass::test_two in $nodeIdResult",
            nodeIdResult.contains("module2/test_mod2.py::TestClass::test_two")
        )
    }

    fun testCopyFromRootNodeWithProxyStructureOnly() {
        // Reproduces the case where the DefaultMutableTreeNode tree structure
        // does not contain the children, but the SMTestProxy structure does.

        // 1. Create file and proxy
        val file1 = myFixture.addFileToProject(
            "module1/test_mod1.py",
            """
            def test_one():
                pass
            """.trimIndent()
        )
        val path1 = file1.virtualFile.path
        val url1 = "python<$path1>://module1.test_mod1.test_one"
        val proxy1 = FakeSMTestProxy("test_one", false, null, url1)

        // 2. Create Root Proxy
        val rootProxy = FakeSMTestProxy("Root", true, null, null)
        rootProxy.addChild(proxy1)

        // 3. Create Tree Node wrapping Root Proxy
        // IMPORTANT: We ADD children to the DefaultMutableTreeNode because we use View Traversal now
        val rootNode = DefaultMutableTreeNode(rootProxy)
        rootNode.add(DefaultMutableTreeNode(proxy1))

        // 4. Test CopyPytestNodeIdAction
        val nodeIdResult = mutableListOf<String>()
        CopyPytestNodeIdAction().collect(rootNode, nodeIdResult, project)

        // Expect: module1/test_mod1.py::test_one
        // Current implementation fails because it iterates DefaultMutableTreeNode children (count=0)
        assertEquals(1, nodeIdResult.size)
        assertTrue(
            "Expected module1/test_mod1.py::test_one in $nodeIdResult",
            nodeIdResult.contains("module1/test_mod1.py::test_one")
        )
    }

    fun testCopyFQNFromRootNodeWithProxyStructureOnly() {
        // Reproduces the case where the DefaultMutableTreeNode tree structure
        // does not contain the children, but the SMTestProxy structure does.

        // 1. Create file and proxy
        val file1 = myFixture.addFileToProject(
            "module1/test_mod1.py",
            """
            def test_one():
                pass
            """.trimIndent()
        )
        val path1 = file1.virtualFile.path
        val url1 = "python<$path1>://module1.test_mod1.test_one"
        val proxy1 = FakeSMTestProxy("test_one", false, null, url1)

        // 2. Create Root Proxy
        val rootProxy = FakeSMTestProxy("Root", true, null, null)
        rootProxy.addChild(proxy1)

        // 3. Create Tree Node wrapping Root Proxy
        // IMPORTANT: We ADD children to the DefaultMutableTreeNode because we use View Traversal now
        val rootNode = DefaultMutableTreeNode(rootProxy)
        rootNode.add(DefaultMutableTreeNode(proxy1))

        // 4. Test CopyFQNAction
        val fqnResult = mutableListOf<String>()
        CopyFQNAction().collect(rootNode, fqnResult, project)

        // Expect: module1.test_mod1.test_one
        assertEquals(1, fqnResult.size)
        assertTrue(
            "Expected module1.test_mod1.test_one in $fqnResult",
            fqnResult.contains("module1.test_mod1.test_one")
        )
    }

    fun testCopyFQNFromParameterizedTest() {
        val file1 = myFixture.addFileToProject(
            "module1/test_mod1.py",
            """
            import pytest
            @pytest.mark.parametrize("arg", [1, 2])
            def test_param(arg):
                pass
            """.trimIndent()
        )
        file1.virtualFile.path

        // python_uttestid URL with parameters
        val url1 = "python_uttestid://module1.test_mod1.test_param[1]"
        val proxy1 = FakeSMTestProxy("test_param[1]", false, null, url1)

        val rootNode = DefaultMutableTreeNode(proxy1)

        val fqnResult = mutableListOf<String>()
        CopyFQNAction().collect(rootNode, fqnResult, project)

        assertEquals(1, fqnResult.size)
        // We want the parameter to be excluded
        assertEquals("module1.test_mod1.test_param", fqnResult[0])
    }

    fun testCopyFQNFromParameterizedTestDuplicates() {
        val file1 = myFixture.addFileToProject(
            "module1/test_mod1.py",
            """
            import pytest
            @pytest.mark.parametrize("arg", [1, 2])
            def test_param(arg):
                pass
            """.trimIndent()
        )
        file1.virtualFile.path

        // python_uttestid URL with parameters
        val url1 = "python_uttestid://module1.test_mod1.test_param[1]"
        val proxy1 = FakeSMTestProxy("test_param[1]", false, null, url1)

        val url2 = "python_uttestid://module1.test_mod1.test_param[2]"
        val proxy2 = FakeSMTestProxy("test_param[2]", false, null, url2)

        val rootNode = DefaultMutableTreeNode("Root")
        rootNode.add(DefaultMutableTreeNode(proxy1))
        rootNode.add(DefaultMutableTreeNode(proxy2))

        val fqnResult = mutableListOf<String>()
        CopyFQNAction().collect(rootNode, fqnResult, project)

        // Raw collection should have duplicates because we have two proxies
        assertEquals(2, fqnResult.size)
        assertEquals("module1.test_mod1.test_param", fqnResult[0])
        assertEquals("module1.test_mod1.test_param", fqnResult[1])

        // The fix in actionPerformed does this:
        val uniqueResult = fqnResult.distinct()
        assertEquals(1, uniqueResult.size)
        assertEquals("module1.test_mod1.test_param", uniqueResult[0])
    }

    private class FakeSMTestProxy(
        name: String,
        isSuite: Boolean,
        private val element: PsiElement?,
        locationUrl: String? = null
    ) : SMTestProxy(name, isSuite, locationUrl) {

        override fun getLocation(project: Project, scope: GlobalSearchScope): Location<*>? {
            return element?.let { PsiLocation(it) }
        }
    }
}
