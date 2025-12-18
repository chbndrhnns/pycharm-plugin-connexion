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
