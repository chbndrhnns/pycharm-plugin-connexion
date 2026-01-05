package com.github.chbndrhnns.intellijplatformplugincopy.navbar

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.ide.ui.UISettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Processor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression

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
        assertNull(
            "Should return null when plugin setting is disabled",
            extension.getPresentableText(function)
        )
    }

    fun testDunderMethodsAreFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                def __init__(self):
                    pass
        """.trimIndent()
        )

        val method = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)
        assertNotNull(method)
        assertNull(
            "Should return null for dunder methods",
            extension.getPresentableText(method)
        )
    }

    fun testDunderStrMethodIsFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                def __str__(self):
                    return "MyClass"
        """.trimIndent()
        )

        val method = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)
        assertNotNull(method)
        assertNull(
            "Should return null for __str__ method",
            extension.getPresentableText(method)
        )
    }

    fun testSingleUnderscoreMethodNotFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                def _private_method(self):
                    pass
        """.trimIndent()
        )

        val method = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)
        assertNotNull(method)
        assertEquals("_private_method()", extension.getPresentableText(method))
    }

    fun testDoubleUnderscorePrefixOnlyNotFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                def __mangled(self):
                    pass
        """.trimIndent()
        )

        val method = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)
        assertNotNull(method)
        assertEquals("__mangled()", extension.getPresentableText(method))
    }

    fun testDunderAttributesAreFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                __annotations__ = {}
        """.trimIndent()
        )

        val targetExpr = PsiTreeUtil.findChildOfType(file, PyTargetExpression::class.java)
        assertNotNull(targetExpr)
        assertNull(
            "Should return null for __annotations__ attribute",
            extension.getPresentableText(targetExpr)
        )
    }

    fun testDunderModuleAttributeIsFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            __module__ = "mymodule"
        """.trimIndent()
        )

        val targetExpr = PsiTreeUtil.findChildOfType(file, PyTargetExpression::class.java)
        assertNotNull(targetExpr)
        assertNull(
            "Should return null for __module__ attribute",
            extension.getPresentableText(targetExpr)
        )
    }

    fun testRegularClassAttributeNotFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                my_attribute = "value"
        """.trimIndent()
        )

        val targetExpr = PsiTreeUtil.findChildOfType(file, PyTargetExpression::class.java)
        assertNotNull(targetExpr)
        assertEquals("my_attribute", extension.getPresentableText(targetExpr))
    }

    fun testSingleUnderscoreAttributeNotFiltered() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                _private_attr = "value"
        """.trimIndent()
        )

        val targetExpr = PsiTreeUtil.findChildOfType(file, PyTargetExpression::class.java)
        assertNotNull(targetExpr)
        assertEquals("_private_attr", extension.getPresentableText(targetExpr))
    }

    fun testNestedClasses() {
        val file = myFixture.configureByText(
            "test.py", """
            class Test:
                class TestInner:
                    pass
        """.trimIndent()
        )

        val classes = PsiTreeUtil.findChildrenOfType(file, PyClass::class.java)
        assertEquals(2, classes.size)
        val outerClass = classes.find { it.name == "Test" }
        val innerClass = classes.find { it.name == "TestInner" }

        assertNotNull(outerClass)
        assertNotNull(innerClass)

        assertEquals("Test", extension.getPresentableText(outerClass))
        assertEquals("TestInner", extension.getPresentableText(innerClass))

        // Verify children processing
        val children = ArrayList<Any>()
        val processor = Processor<Any> {
            children.add(it)
            true
        }
        extension.processChildren(outerClass!!, null, processor)
        assertTrue("Outer class should have TestInner as child", children.contains(innerClass as Any))
    }

    fun testDunderMethodsAreFilteredFromChildren() {
        val file = myFixture.configureByText(
            "test.py", """
            class MyClass:
                def __init__(self):
                    pass
                def normal_method(self):
                    pass
        """.trimIndent()
        )

        val pyClass = PsiTreeUtil.findChildOfType(file, PyClass::class.java)
        assertNotNull(pyClass)

        val children = ArrayList<Any>()
        val processor2 = Processor<Any> {
            children.add(it)
            true
        }
        extension.processChildren(pyClass!!, null, processor2)

        val names = children.filterIsInstance<PyFunction>().map { it.name }
        assertFalse("Dunder methods should be filtered from children", names.contains("__init__"))
        assertTrue("Normal methods should be included in children", names.contains("normal_method"))
    }

    fun testAdjustElementDoesNotGoBeyondFile() {
        val file = myFixture.configureByText("test.py", "pass")
        val directory = file.containingDirectory
        assertNotNull(directory)

        val adjusted = extension.adjustElement(directory)
        assertEquals("Should not adjust directory to something else", directory, adjusted)
    }

    fun testAdjustElementWithNonPythonElement() {
        val file = myFixture.configureByText("test.txt", "some text")
        val adjusted = extension.adjustElement(file)
        assertEquals("Should not adjust non-Python file", file, adjusted)
    }
}
