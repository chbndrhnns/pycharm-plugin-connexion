package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import fixtures.TestBase

class PyIntroduceParameterObjectValidationTest : TestBase() {

    fun testValidateClassName() {
        myFixture.configureByText("a.py", "def foo(a, b): pass")
        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!
        val params = function.parameterList.parameters.filterIsInstance<PyNamedParameter>()

        val validator = IntroduceParameterObjectValidator(function)

        // Valid name
        assertNull(validator.validate("MyClass", "params", params))

        // Invalid names
        assertNotNull(validator.validate("", "params", params))
        assertNotNull(validator.validate("123Class", "params", params))
        assertNotNull(validator.validate("class", "params", params))
        assertNotNull(validator.validate("My-Class", "params", params))
    }

    fun testValidateParameterName() {
        myFixture.configureByText("a.py", "def foo(a, b): pass")
        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!
        val params = function.parameterList.parameters.filterIsInstance<PyNamedParameter>()

        val validator = IntroduceParameterObjectValidator(function)

        // Valid name
        assertNull(validator.validate("MyClass", "params", params))

        // Invalid names
        assertNotNull(validator.validate("MyClass", "", params))
        assertNotNull(validator.validate("MyClass", "123params", params))
        assertNotNull(validator.validate("MyClass", "def", params))
        assertNotNull(validator.validate("MyClass", "param-s", params))
    }

    fun testValidateAtLeastOneParameterSelected() {
        myFixture.configureByText("a.py", "def foo(a, b): pass")
        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!
        val params = function.parameterList.parameters.filterIsInstance<PyNamedParameter>()

        val validator = IntroduceParameterObjectValidator(function)

        // No parameters selected
        assertNotNull(validator.validate("MyClass", "params", emptyList()))
    }

    fun testDuplicateClassName() {
        myFixture.configureByText(
            "a.py", """
            class ExistingClass:
                pass
                
            def foo(a, b): 
                pass
        """.trimIndent()
        )
        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!
        val params = function.parameterList.parameters.filterIsInstance<PyNamedParameter>()

        val validator = IntroduceParameterObjectValidator(function)

        // Duplicate name
        assertNotNull(validator.validate("ExistingClass", "params", params))

        // Non-duplicate name
        assertNull(validator.validate("NewClass", "params", params))
    }
}
