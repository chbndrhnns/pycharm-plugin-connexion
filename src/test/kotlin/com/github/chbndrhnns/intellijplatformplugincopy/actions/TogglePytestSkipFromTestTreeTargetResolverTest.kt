package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase
import javax.swing.tree.DefaultMutableTreeNode

class TogglePytestSkipFromTestTreeTargetResolverTest : TestBase() {

    fun testLeafFunctionResolvesToFunction() {
        myFixture.configureByText(
            "test_simple.py",
            """
            def test_foo():
                pass
            """.trimIndent()
        )

        val function = myFixture.findElementByText("test_foo", PyFunction::class.java)
        val path = myFixture.file.virtualFile.path
        val proxy = FakeSMTestProxy(
            "test_foo",
            isSuite = false,
            element = function,
            locationUrl = "python<$path>://test_simple.test_foo"
        )
        val node = DefaultMutableTreeNode(proxy)

        val target = TogglePytestSkipFromTestTreeTargetResolver.resolve(node, project)
        assertTrue(target is TogglePytestSkipFromTestTreeTargetResolver.Target.Function)
        val fn = (target as TogglePytestSkipFromTestTreeTargetResolver.Target.Function).function
        assertEquals("test_foo", fn.name)
    }

    fun testSuiteClassFallsBackToDescendantLeafAndResolvesToClass() {
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
        val leafProxy = FakeSMTestProxy(
            "test_method",
            isSuite = false,
            element = method,
            locationUrl = "python<$path>://test_class.TestMyClass.test_method"
        )

        val suiteProxy = SMTestProxy("TestMyClass", true, null)
        val suiteNode = DefaultMutableTreeNode(suiteProxy).apply {
            add(DefaultMutableTreeNode(leafProxy))
        }

        val target = TogglePytestSkipFromTestTreeTargetResolver.resolve(suiteNode, project)
        assertTrue(target is TogglePytestSkipFromTestTreeTargetResolver.Target.Clazz)
        val cls = (target as TogglePytestSkipFromTestTreeTargetResolver.Target.Clazz).clazz
        assertEquals("TestMyClass", cls.name)
    }

    fun testParametrizedLeafResolvesToTestMethodNotParameterInstance() {
        myFixture.configureByText(
            "test_param.py",
            """
            import pytest

            @pytest.mark.parametrize("arg", [1, 2, 3])
            def test_jump(arg):
                pass
            """.trimIndent()
        )

        val function = myFixture.findElementByText("test_jump", PyFunction::class.java)
        val path = myFixture.file.virtualFile.path

        // Simulate a parameter leaf by name; we still expect the target to be the method.
        val proxy = FakeSMTestProxy(
            "test_jump[2]",
            isSuite = false,
            element = function,
            locationUrl = "python<$path>://test_param.test_jump"
        )
        val node = DefaultMutableTreeNode(proxy)

        val target = TogglePytestSkipFromTestTreeTargetResolver.resolve(node, project)
        assertTrue(target is TogglePytestSkipFromTestTreeTargetResolver.Target.Function)
        val fn = (target as TogglePytestSkipFromTestTreeTargetResolver.Target.Function).function
        assertEquals("test_jump", fn.name)
    }

    fun testSuiteModuleResolvesToModule() {
        myFixture.configureByText(
            "test_mod.py",
            """
            def test_foo():
                pass
            """.trimIndent()
        )

        val function = myFixture.findElementByText("test_foo", PyFunction::class.java)
        val path = myFixture.file.virtualFile.path
        val leafProxy = FakeSMTestProxy(
            "test_foo",
            isSuite = false,
            element = function,
            locationUrl = "python<$path>://test_mod.test_foo"
        )

        val suiteProxy = SMTestProxy("test_mod.py", true, null)
        val suiteNode = DefaultMutableTreeNode(suiteProxy).apply {
            add(DefaultMutableTreeNode(leafProxy))
        }

        val target = TogglePytestSkipFromTestTreeTargetResolver.resolve(suiteNode, project)
        assertTrue(target is TogglePytestSkipFromTestTreeTargetResolver.Target.Module)
        val file = (target as TogglePytestSkipFromTestTreeTargetResolver.Target.Module).file
        assertEquals("test_mod.py", file.name)
    }

    private class FakeSMTestProxy(
        name: String,
        isSuite: Boolean,
        private val element: PsiElement?,
        locationUrl: String? = null,
    ) : SMTestProxy(name, isSuite, locationUrl) {

        override fun getLocation(project: Project, scope: GlobalSearchScope): Location<*>? {
            return element?.let { PsiLocation(it) }
        }
    }
}
