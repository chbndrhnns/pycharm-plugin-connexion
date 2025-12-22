package com.github.chbndrhnns.intellijplatformplugincopy.pytest.testtree

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PytestTestContextUtilsTest : TestBase() {

    fun testIsTestFile() {
        myFixture.configureByText("test_file.py", "")
        assertTrue(PytestTestContextUtils.isTestFile(myFixture.file as PyFile))

        myFixture.configureByText("file_test.py", "")
        assertTrue(PytestTestContextUtils.isTestFile(myFixture.file as PyFile))

        myFixture.configureByText("normal_file.py", "")
        assertFalse(PytestTestContextUtils.isTestFile(myFixture.file as PyFile))
    }

    fun testIsTestFunction() {
        myFixture.configureByText(
            "test_file.py", """
            def test_func(): pass
            def other_func(): pass
        """.trimIndent()
        )
        val testFunc = myFixture.findElementByText("test_func", PyFunction::class.java)
        val otherFunc = myFixture.findElementByText("other_func", PyFunction::class.java)

        assertTrue(PytestTestContextUtils.isTestFunction(testFunc))
        assertFalse(PytestTestContextUtils.isTestFunction(otherFunc))
    }

    fun testIsTestClass() {
        myFixture.configureByText(
            "test_file.py", """
            class TestClass: pass
            class OtherClass: pass
        """.trimIndent()
        )
        val testClass = myFixture.findElementByText("TestClass", PyClass::class.java)
        val otherClass = myFixture.findElementByText("OtherClass", PyClass::class.java)

        assertTrue(PytestTestContextUtils.isTestClass(testClass))
        assertFalse(PytestTestContextUtils.isTestClass(otherClass))
    }

    fun testIsInTestContext() {
        myFixture.configureByText(
            "test_file.py", """
            class TestClass:
                def method(self):
                    <caret>pass
            
            def test_func():
                <caret>pass
                
            def normal_func():
                <caret>pass
        """.trimIndent()
        )

        val testClassMethodCaret = myFixture.file.findElementAt(myFixture.file.text.indexOf("pass"))!!
        assertTrue(PytestTestContextUtils.isInTestContext(testClassMethodCaret))

        val testFuncCaret = myFixture.file.findElementAt(
            myFixture.file.text.indexOf(
                "pass",
                myFixture.file.text.indexOf("test_func")
            )
        )!!
        assertTrue(PytestTestContextUtils.isInTestContext(testFuncCaret))

        val normalFuncCaret = myFixture.file.findElementAt(
            myFixture.file.text.indexOf(
                "pass",
                myFixture.file.text.indexOf("normal_func")
            )
        )!!
        assertFalse(PytestTestContextUtils.isInTestContext(normalFuncCaret))
    }
}
