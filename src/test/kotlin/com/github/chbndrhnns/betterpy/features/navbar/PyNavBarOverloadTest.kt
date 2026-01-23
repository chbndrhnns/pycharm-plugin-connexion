package com.github.chbndrhnns.betterpy.features.navbar

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.ide.ui.UISettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Processor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction

class PyNavBarOverloadTest : BasePlatformTestCase() {

    private lateinit var extension: PyNavBarModelExtension
    private var originalShowMembers: Boolean = false
    private var originalPluginSetting: Boolean = false

    override fun setUp() {
        super.setUp()
        extension = NavBarModelExtension.EP_NAME.findExtension(PyNavBarModelExtension::class.java)
            ?: throw AssertionError("PyNavBarModelExtension not registered")
        originalShowMembers = UISettings.getInstance().showMembersInNavigationBar
        UISettings.getInstance().showMembersInNavigationBar = true
        originalPluginSetting = PluginSettingsState.instance().state.enablePythonNavigationBar
        PluginSettingsState.instance().state.enablePythonNavigationBar = true
    }

    override fun tearDown() {
        try {
            UISettings.getInstance().showMembersInNavigationBar = originalShowMembers
            PluginSettingsState.instance().state.enablePythonNavigationBar = originalPluginSetting
        } finally {
            super.tearDown()
        }
    }

    fun testOverloadedMethodsAreFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            from typing import overload

            class MyClass:
                @overload
                def method(self, a: int) -> int: ...
                
                @overload
                def method(self, a: str) -> str: ...
                
                def method(self, a: int | str) -> int | str:
                    return a
        """.trimIndent()
        )

        val pyClass = PsiTreeUtil.findChildOfType(file, PyClass::class.java)
        assertNotNull(pyClass)

        val children = ArrayList<Any>()
        val processor = Processor<Any> {
            children.add(it)
            true
        }
        extension.processChildren(pyClass!!, null, processor)

        val methods = children.filterIsInstance<PyFunction>()
        val overloadMethods = methods.filter { it.decoratorList?.findDecorator("overload") != null }
        val normalMethods = methods.filter { it.decoratorList?.findDecorator("overload") == null }

        assertEquals("Should only have one 'method' (the implementation)", 1, methods.size)
        assertEquals("Implementation method should be present", "method", methods[0].name)
        assertTrue("Overloaded methods should be filtered", overloadMethods.isEmpty())
        assertFalse("Normal method should not be filtered", normalMethods.isEmpty())
    }

    fun testOverloadedFunctionsAreFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            from typing import overload

            @overload
            def func(a: int) -> int: ...
            
            @overload
            def func(a: str) -> str: ...
            
            def func(a: int | str) -> int | str:
                return a
        """.trimIndent()
        )

        val children = ArrayList<Any>()
        val processor = Processor<Any> {
            children.add(it)
            true
        }
        extension.processChildren(file, null, processor)

        val functions = children.filterIsInstance<PyFunction>()
        assertEquals("Should only have one 'func' (the implementation)", 1, functions.size)
        assertEquals("Implementation function should be present", "func", functions[0].name)
        val hasOverload = functions.any { it.decoratorList?.findDecorator("overload") != null }
        assertFalse("Overloaded function should be filtered", hasOverload)
    }

    fun testNestedClassWithOverload() {
        val file = myFixture.configureByText(
            "test.py", """
            from typing import overload

            class Outer:
                class Inner:
                    @overload
                    def method(self, a: int) -> int: ...
                    
                    def method(self, a: int) -> int:
                        return a
        """.trimIndent()
        )

        val classes = PsiTreeUtil.findChildrenOfType(file, PyClass::class.java)
        val innerClass = classes.find { it.name == "Inner" }
        assertNotNull(innerClass)

        val children = ArrayList<Any>()
        val processor = Processor<Any> {
            children.add(it)
            true
        }
        extension.processChildren(innerClass!!, null, processor)

        val methods = children.filterIsInstance<PyFunction>()
        assertEquals("Should only have one 'method' in nested class", 1, methods.size)
        val hasOverload = methods.any { it.decoratorList?.findDecorator("overload") != null }
        assertFalse("Overloaded method in nested class should be filtered", hasOverload)
    }
}
