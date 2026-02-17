package com.github.chbndrhnns.betterpy.features.refactoring.move

import com.github.chbndrhnns.betterpy.features.refactoring.move.ui.PyMoveDialog
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PyMoveDialogValidationTest : TestBase() {

    fun testRejectsBlankModulePath() {
        myFixture.configureByText(
            "a.py",
            """
            def helper():
                return 1
            """.trimIndent()
        )

        val function = myFixture.file.findElementAt(0)!!.parent as? PyFunction
            ?: myFixture.file.findElementAt(0)!!.containingFile.children.filterIsInstance<PyFunction>().first()

        val dialog = PyMoveDialog(project, listOf(function))

        val field = dialog.javaClass.getDeclaredField("modulePathField")
        field.isAccessible = true
        val textField = field.get(dialog) as com.intellij.ui.EditorTextField
        textField.text = ""

        val doValidate = dialog.javaClass.getDeclaredMethod("doValidate")
        doValidate.isAccessible = true
        val validationInfo = doValidate.invoke(dialog) as? com.intellij.openapi.ui.ValidationInfo

        assertNotNull("Expected validation error for blank module path", validationInfo)
        assertEquals("Target module path is required", validationInfo?.message)
    }

    fun testAcceptsValidModulePath() {
        myFixture.configureByText(
            "a.py",
            """
            def helper():
                return 1
            """.trimIndent()
        )

        val function = myFixture.file.findElementAt(0)!!.parent as? PyFunction
            ?: myFixture.file.findElementAt(0)!!.containingFile.children.filterIsInstance<PyFunction>().first()

        val dialog = PyMoveDialog(project, listOf(function))

        val field = dialog.javaClass.getDeclaredField("modulePathField")
        field.isAccessible = true
        val textField = field.get(dialog) as com.intellij.ui.EditorTextField
        textField.text = "pkg.target"

        val doValidate = dialog.javaClass.getDeclaredMethod("doValidate")
        doValidate.isAccessible = true
        val validationInfo = doValidate.invoke(dialog) as? com.intellij.openapi.ui.ValidationInfo

        assertNull("Expected no validation error for valid module path", validationInfo)
    }
}
