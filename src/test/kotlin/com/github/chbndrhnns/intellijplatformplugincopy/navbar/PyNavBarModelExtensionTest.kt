package com.github.chbndrhnns.intellijplatformplugincopy.navbar

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.ide.ui.UISettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction

class PyNavBarModelExtensionTest : BasePlatformTestCase() {

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

    fun testPresentableTextForFunction() {
        val file = myFixture.configureByText(
            "test.py", """
            def foo():
                pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)
        assertNotNull(function)
        assertEquals("foo()", extension.getPresentableText(function))
    }

    fun testPresentableTextForAsyncFunction() {
        val file = myFixture.configureByText(
            "test.py", """
            async def async_foo():
                pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)
        assertNotNull(function)
        assertEquals("async async_foo()", extension.getPresentableText(function))
    }

    fun testPresentableTextForClass() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                pass
        """.trimIndent()
        )

        val cls = PsiTreeUtil.findChildOfType(file, PyClass::class.java)
        assertNotNull(cls)
        assertEquals("MyClass", extension.getPresentableText(cls))
    }

    fun testPresentableTextForMethod() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                def my_method(self):
                    pass
        """.trimIndent()
        )

        val method = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)
        assertNotNull(method)
        assertEquals("my_method()", extension.getPresentableText(method))
    }

    fun testAdjustElementReturnsElementAsIs() {
        val file = myFixture.configureByText(
            "test.py", """
            def foo():
                pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)
        assertNotNull(function)
        assertSame(function, extension.adjustElement(function!!))
    }

    fun testNavigationBarShowsMembers() {
        myFixture.configureByText(
            "test.py", """
            class Calculator:
                def add(self, a, b):
                    return a + <caret>b
        """.trimIndent()
        )

        val breadcrumbs = myFixture.getBreadcrumbsAtCaret()

        assertTrue(
            "Should show class in breadcrumbs",
            breadcrumbs.any { it.text == "Calculator" })
        assertTrue(
            "Should show method in breadcrumbs",
            breadcrumbs.any { it.text == "add()" })
    }

    fun testDisabledWhenPluginSettingOff() {
        PluginSettingsState.instance().state.enablePythonNavigationBar = false

        val file = myFixture.configureByText(
            "test.py", """
            def foo():
                pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)
        assertNotNull(function)
        assertNull("Should return null when plugin setting is disabled", 
                   extension.getPresentableText(function))
    }
}
