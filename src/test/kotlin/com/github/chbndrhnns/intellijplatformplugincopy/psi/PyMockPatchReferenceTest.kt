package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PyMockPatchReferenceTest : TestBase() {

    override fun setUp() {
        super.setUp()
        // Setup stubs
        // create unittest/mock.py
        myFixture.addFileToProject("unittest/mock.py", "def patch(target, new=None): pass")
        myFixture.addFileToProject("unittest/__init__.py", "")
    }

    fun testMockPatchResolution() {
        myFixture.addFileToProject("os/path.py", "def exists(path): pass")
        myFixture.addFileToProject("os/__init__.py", "")

        myFixture.configureByText(
            "test_mock_patch.py", """
            from unittest.mock import patch
            import os.path
            
            @patch('os.path.ex<caret>ists') 
            def test_something(mock_exists):
                pass
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertInstanceOf(element, PyFunction::class.java)
        assertEquals("exists", (element as PyFunction).name)
    }

    fun testMockPatchClassResolution() {
        myFixture.addFileToProject(
            "MyModule.py", """
            class MyClass:
                def method(self): pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_foo.py", """
            from unittest import mock
            
            def test_foo():
                with mock.patch('MyMo<caret>dule.MyClass'):
                    pass
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertInstanceOf(element, PyClass::class.java)
        assertEquals("MyClass", (element as PyClass).name)
    }
}
