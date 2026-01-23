package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.intellij.openapi.command.WriteCommandAction
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PytestSkipTogglerTest : TestBase() {

    fun testToggleOnFunctionAddsAndRemovesDecorator() {
        myFixture.configureByText(
            "test_foo.py",
            """
            def test_something():
                pass
            """.trimIndent()
        )

        val file = myFixture.file as PyFile
        val function = myFixture.findElementByText("test_something", PyFunction::class.java)
        val toggler = PytestSkipToggler(PyElementGenerator.getInstance(project))

        WriteCommandAction.runWriteCommandAction(project) {
            toggler.toggleOnFunction(function, file)
        }

        assertTrue(file.text.contains("@pytest.mark.skip"))
        assertTrue(file.text.contains("import pytest"))

        WriteCommandAction.runWriteCommandAction(project) {
            toggler.toggleOnFunction(function, file)
        }

        assertFalse(file.text.contains("@pytest.mark.skip"))
    }

    fun testToggleOnClassAddsDecorator() {
        myFixture.configureByText(
            "test_foo.py",
            """
            class TestClass:
                def test_something(self):
                    pass
            """.trimIndent()
        )

        val file = myFixture.file as PyFile
        val clazz = myFixture.findElementByText("TestClass", PyClass::class.java)
        val toggler = PytestSkipToggler(PyElementGenerator.getInstance(project))

        WriteCommandAction.runWriteCommandAction(project) {
            toggler.toggleOnClass(clazz, file)
        }

        assertTrue(file.text.contains("@pytest.mark.skip"))
        assertTrue(file.text.contains("class TestClass"))
    }

    fun testToggleOnModuleAddsPytestmark() {
        myFixture.configureByText(
            "test_foo.py",
            """
            def test_something():
                pass
            """.trimIndent()
        )

        val file = myFixture.file as PyFile
        val toggler = PytestSkipToggler(PyElementGenerator.getInstance(project))

        WriteCommandAction.runWriteCommandAction(project) {
            toggler.toggleOnModule(file)
        }

        assertTrue(file.text.contains("pytestmark = [pytest.mark.skip]"))
        assertTrue(file.text.contains("import pytest"))
    }
}
