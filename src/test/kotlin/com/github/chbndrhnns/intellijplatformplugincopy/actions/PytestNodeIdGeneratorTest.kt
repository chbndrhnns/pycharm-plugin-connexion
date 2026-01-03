package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.pytest.testtree.PytestNodeIdGenerator
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PytestNodeIdGeneratorTest : TestBase() {

    fun testSimpleFunction() {
        myFixture.configureByText(
            "test_simple.py",
            """
            def test_foo():
                pass
            """.trimIndent()
        )
        val function = myFixture.findElementByText("test_foo", PyFunction::class.java)
        val path = myFixture.file.virtualFile.path
        val locationUrl = "python<$path>://test_simple.test_foo"
        val proxy = FakeSMTestProxy("test_foo", false, function, locationUrl)

        val record = PytestNodeIdGenerator.parseProxy(proxy, project)
        assertEquals("test_simple.py::test_foo", record!!.nodeid)

        val fromPsi = PytestNodeIdGenerator.fromPsiElement(function, project)
        assertEquals("test_simple.py::test_foo", fromPsi)
    }

    fun testClassMethod() {
        myFixture.configureByText(
            "test_class.py",
            """
            class TestMyClass:
                def test_method(self):
                    pass
            """.trimIndent()
        )
        val method = myFixture.findElementByText("test_method", PyFunction::class.java)
        val path = myFixture.file.virtualFile.path
        val locationUrl = "python<$path>://test_class.TestMyClass.test_method"
        val proxy = FakeSMTestProxy("test_method", false, method, locationUrl)

        val id = PytestNodeIdGenerator.parseProxy(proxy, project)
        assertEquals("test_class.py::TestMyClass::test_method", id!!.nodeid)
    }

    fun testNestedClassMethod() {
        myFixture.configureByText(
            "test_nested.py",
            """
            class TestOuter:
                class TestInner:
                    def test_inner(self):
                        pass
            """.trimIndent()
        )
        val method = myFixture.findElementByText("test_inner", PyFunction::class.java)
        val path = myFixture.file.virtualFile.path
        val locationUrl = "python<$path>://test_nested.TestOuter.TestInner.test_inner"
        val proxy = FakeSMTestProxy("test_inner", false, method, locationUrl)

        val record = PytestNodeIdGenerator.parseProxy(proxy, project)
        assertEquals("test_nested.py::TestOuter::TestInner::test_inner", record!!.nodeid)
    }

    fun testParametrizedFromCaretElementSelectsLeaf() {
        myFixture.configureByText(
            "test_param.py",
            """
            import pytest


            @pytest.mark.parametrize("arg", [1, 2, 3,])
            def test_jump(arg):
                assert False
            """.trimIndent()
        )

        val offset = myFixture.file.text.indexOf("2")
        assertTrue(offset >= 0)
        val valueLeaf = myFixture.file.findElementAt(offset)!!

        val pyFile = myFixture.file as PyFile
        val function = pyFile.topLevelFunctions.firstOrNull { it.name == "test_jump" }
        assertNotNull(function)
        assertNotNull(function!!.decoratorList)
        assertTrue(function.decoratorList!!.text.contains("parametrize"))

        val nodeId = PytestNodeIdGenerator.fromCaretElement(valueLeaf, project)
        assertEquals("test_param.py::test_jump[2]", nodeId)
    }

    /**
     * Tests the scenario where we have 3-level nested classes and the proxy parent chain
     * contains the full hierarchy. This simulates the real test tree structure:
     * root -> file -> TestParent -> TestChild -> TestGrandChild -> test_
     *
     * The locationUrl may only contain the top-level class, but the proxy parent chain
     * should give us the full nested class hierarchy.
     */
    fun testThreeLevelNestedClassWithProxyParentChain() {
        myFixture.configureByText(
            "test_nested_deep.py",
            """
            class TestParent:
                class TestChild:
                    class TestGrandChild:
                        def test_(self):
                            assert 1 == 2
            """.trimIndent()
        )

        val method = myFixture.findElementByText("test_", PyFunction::class.java)
        val path = myFixture.file.virtualFile.path

        // Simulate the real test tree structure with proxy parent chain
        // The locationUrl only has the top-level class (simulating the bug scenario)
        val locationUrl = "python<$path>://test_nested_deep.TestParent"

        // Build the proxy parent chain: root -> file -> TestParent -> TestChild -> TestGrandChild -> test_
        val rootProxy = FakeSMTestProxy("Root", true, null, null)
        val fileProxy = FakeSMTestProxy("test_nested_deep.py", true, null, null)
        val parentProxy = FakeSMTestProxy("TestParent", true, null, null)
        val childProxy = FakeSMTestProxy("TestChild", true, null, null)
        val grandChildProxy = FakeSMTestProxy("TestGrandChild", true, null, null)
        val methodProxy = FakeSMTestProxy("test_", false, method, locationUrl)

        // Set up parent chain
        rootProxy.addChild(fileProxy)
        fileProxy.addChild(parentProxy)
        parentProxy.addChild(childProxy)
        childProxy.addChild(grandChildProxy)
        grandChildProxy.addChild(methodProxy)

        val record = PytestNodeIdGenerator.parseProxy(methodProxy, project)

        // Should get the full nested class hierarchy from the proxy parent chain
        assertEquals("test_nested_deep.py::TestParent::TestChild::TestGrandChild::test_", record!!.nodeid)
    }

    /**
     * Tests the bug where a method named "test_" in a file named "test_.py" gets cut off.
     * The filtering logic incorrectly removes the method name because it matches the module name.
     * 
     * Expected: tests/test_.py::TestParent::TestChild::TestGrandChild::test_
     * Bug produces: tests/test_.py::TestParent::TestChild::TestGrandChild (missing ::test_)
     */
    fun testMethodNameMatchesModuleName() {
        val file = myFixture.addFileToProject(
            "tests/test_.py",
            """
            class TestParent:
                class TestChild:
                    class TestGrandChild:
                        def test_(self):
                            assert 1 == 2
            """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val method = myFixture.findElementByText("test_", PyFunction::class.java)

        // Simulate the REAL pytest tree structure with directory nodes
        // root -> tests (dir) -> test_ (module without .py) -> TestParent -> TestChild -> TestGrandChild -> test_
        val locationUrl = "python<${file.virtualFile.path}>://tests.test_.TestParent"

        val rootProxy = FakeSMTestProxy("Root", true, null, null)
        val dirProxy = FakeSMTestProxy("tests", true, null, null)  // Directory node
        val moduleProxy = FakeSMTestProxy("test_", true, null, null)  // Module node (no .py)
        val parentProxy = FakeSMTestProxy("TestParent", true, null, null)
        val childProxy = FakeSMTestProxy("TestChild", true, null, null)
        val grandChildProxy = FakeSMTestProxy("TestGrandChild", true, null, null)
        val methodProxy = FakeSMTestProxy("test_", false, method, locationUrl)  // Method has same name as module!

        // Set up parent chain
        rootProxy.addChild(dirProxy)
        dirProxy.addChild(moduleProxy)
        moduleProxy.addChild(parentProxy)
        parentProxy.addChild(childProxy)
        childProxy.addChild(grandChildProxy)
        grandChildProxy.addChild(methodProxy)

        val record = PytestNodeIdGenerator.parseProxy(methodProxy, project)

        // Should get: tests/test_.py::TestParent::TestChild::TestGrandChild::test_
        // NOT: tests/test_.py::TestParent::TestChild::TestGrandChild (missing the method!)
        assertEquals("tests/test_.py::TestParent::TestChild::TestGrandChild::test_", record!!.nodeid)
    }

    /**
     * Tests the real pytest tree structure which includes directory nodes.
     * Real structure: root -> tests (dir) -> test_ (module name without .py) -> TestParent -> ...
     * 
     * This reproduces the bug where we get duplicated paths like:
     * tests/test_.py::tests::test_::TestParent::TestChild::TestGrandChild::test_method
     */
    fun testNestedClassWithDirectoryNodesInProxyChain() {
        val file = myFixture.addFileToProject(
            "tests/test_.py",
            """
            class TestParent:
                class TestChild:
                    class TestGrandChild:
                        def test_method(self):
                            assert 1 == 2
            """.trimIndent()
        )

        // Configure the fixture to use this file so findElementByText works
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val method = myFixture.findElementByText("test_method", PyFunction::class.java)

        // Simulate the REAL pytest tree structure with directory nodes
        // root -> tests (dir) -> test_ (module without .py) -> TestParent -> TestChild -> TestGrandChild -> test_method
        val locationUrl = "python<${file.virtualFile.path}>://tests.test_.TestParent"

        val rootProxy = FakeSMTestProxy("Root", true, null, null)
        val dirProxy = FakeSMTestProxy("tests", true, null, null)  // Directory node
        val moduleProxy = FakeSMTestProxy("test_", true, null, null)  // Module node (no .py)
        val parentProxy = FakeSMTestProxy("TestParent", true, null, null)
        val childProxy = FakeSMTestProxy("TestChild", true, null, null)
        val grandChildProxy = FakeSMTestProxy("TestGrandChild", true, null, null)
        val methodProxy = FakeSMTestProxy("test_method", false, method, locationUrl)

        // Set up parent chain
        rootProxy.addChild(dirProxy)
        dirProxy.addChild(moduleProxy)
        moduleProxy.addChild(parentProxy)
        parentProxy.addChild(childProxy)
        childProxy.addChild(grandChildProxy)
        grandChildProxy.addChild(methodProxy)

        val record = PytestNodeIdGenerator.parseProxy(methodProxy, project)

        // Should get: tests/test_.py::TestParent::TestChild::TestGrandChild::test_method
        // NOT: tests/test_.py::tests::test_::TestParent::TestChild::TestGrandChild::test_method
        assertEquals("tests/test_.py::TestParent::TestChild::TestGrandChild::test_method", record!!.nodeid)
    }

    /**
     * Tests that parametrized test names with parentheses format are converted to square brackets.
     * 
     * Expected: tests/test_.py::TestParent::TestChild::TestGrandChild::test_[1]
     * Bug produces: tests/test_.py::TestParent::TestChild::TestGrandChild::test_::(1)
     */
    fun testParametrizedNestedClassWithParenthesesFormat() {
        val file = myFixture.addFileToProject(
            "tests/test_.py",
            """
            class TestParent:
                class TestChild:
                    class TestGrandChild:
                        def test_(self):
                            assert 1 == 2
            """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val method = myFixture.findElementByText("test_", PyFunction::class.java)

        // Simulate pytest tree with parametrized test using parentheses format
        // root -> tests (dir) -> test_ (module) -> TestParent -> TestChild -> TestGrandChild -> test_(1)
        val locationUrl = "python<${file.virtualFile.path}>://tests.test_.TestParent"

        val rootProxy = FakeSMTestProxy("Root", true, null, null)
        val dirProxy = FakeSMTestProxy("tests", true, null, null)
        val moduleProxy = FakeSMTestProxy("test_", true, null, null)
        val parentProxy = FakeSMTestProxy("TestParent", true, null, null)
        val childProxy = FakeSMTestProxy("TestChild", true, null, null)
        val grandChildProxy = FakeSMTestProxy("TestGrandChild", true, null, null)
        // Proxy name uses parentheses format (1) instead of square brackets [1]
        val methodProxy = FakeSMTestProxy("test_(1)", false, method, locationUrl)

        // Set up parent chain
        rootProxy.addChild(dirProxy)
        dirProxy.addChild(moduleProxy)
        moduleProxy.addChild(parentProxy)
        parentProxy.addChild(childProxy)
        childProxy.addChild(grandChildProxy)
        grandChildProxy.addChild(methodProxy)

        val record = PytestNodeIdGenerator.parseProxy(methodProxy, project)

        // Should convert (1) to [1] in the node ID
        assertEquals("tests/test_.py::TestParent::TestChild::TestGrandChild::test_[1]", record!!.nodeid)
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
