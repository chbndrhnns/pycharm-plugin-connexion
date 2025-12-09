package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
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
        val proxy = FakeSMTestProxy("test_foo", false, function)

        val id = PytestNodeIdGenerator.getId(proxy, project)
        assertEquals("test_simple.py::test_foo", id)
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
        val proxy = FakeSMTestProxy("test_method", false, method)

        val id = PytestNodeIdGenerator.getId(proxy, project)
        assertEquals("test_class.py::TestMyClass::test_method", id)
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
        val proxy = FakeSMTestProxy("test_inner", false, method)

        val id = PytestNodeIdGenerator.getId(proxy, project)
        assertEquals("test_nested.py::TestOuter::TestInner::test_inner", id)
    }

    fun testFallbackHierarchy() {
        // Create a hierarchy of proxies without PSI
        val root = FakeSMTestProxy("root", true, null)
        val fileProxy = FakeSMTestProxy("test_fallback.py", true, null)
        val classProxy = FakeSMTestProxy("TestFallback", true, null)
        val methodProxy = FakeSMTestProxy("test_fallback_method", false, null)

        root.addChild(fileProxy)
        fileProxy.addChild(classProxy)
        classProxy.addChild(methodProxy)

        val id = PytestNodeIdGenerator.getId(methodProxy, project)
        // Expected behavior of fallback: traverse up to parent
        // fileProxy -> classProxy -> methodProxy
        // Note: Logic currently stops at parent != null, so root is excluded if it has no parent?
        // Wait, loop: while (current != null && current.parent != null)
        // methodProxy (parent=classProxy) -> add "test_fallback_method"
        // classProxy (parent=fileProxy) -> add "TestFallback"
        // fileProxy (parent=root) -> add "test_fallback.py"
        // root (parent=null) -> stop.

        assertEquals("test_fallback.py::TestFallback::test_fallback_method", id)
    }

    private class FakeSMTestProxy(
        name: String,
        isSuite: Boolean,
        private val element: PsiElement?
    ) : SMTestProxy(name, isSuite, null) {

        override fun getLocation(project: Project, scope: GlobalSearchScope): Location<*>? {
            return element?.let { PsiLocation(it) }
        }
    }
}
