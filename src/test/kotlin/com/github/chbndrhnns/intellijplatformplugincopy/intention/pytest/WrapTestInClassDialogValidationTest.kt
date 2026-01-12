package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.jetbrains.python.psi.PyFile
import fixtures.TestBase

class WrapTestInClassDialogValidationTest : TestBase() {

    fun testWarnsOnFunctionCollisionInTargetClass() {
        myFixture.configureByText(
            "test_collision.py",
            """
            class TestSource:
                def test_duplicate(self):
                    pass

            class TestTarget:
                def test_duplicate(self):
                    pass
            
            class TestSafe:
                pass
            """
        )

        val file = myFixture.file as PyFile
        val classes = file.topLevelClasses
        val sourceClass = classes.find { it.name == "TestSource" }!!
        val targetClass = classes.find { it.name == "TestTarget" }!!
        val safeClass = classes.find { it.name == "TestSafe" }!!

        val existingClasses = listOf(sourceClass, targetClass, safeClass)

        // Case 1: Collision
        val dialog = WrapTestInClassDialog(
            project,
            "TestNew",
            existingTestClasses = existingClasses,
            sourceFunctionName = "test_duplicate"
        )

        // Access private fields via reflection or helper if needed, 
        // but wait, we need to simulate UI selection.
        // The dialog fields are private. 
        // But we can use reflection to set the selection.

        // Actually, let's look at WrapTestInClassDialog again.
        // It uses JRadioButton and ComboBox.
        // We can use reflection to access them and simulate user input.

        val addToExistingRadioField = dialog.javaClass.getDeclaredField("addToExistingClassRadio")
        addToExistingRadioField.isAccessible = true
        val addToExistingRadio = addToExistingRadioField.get(dialog) as javax.swing.JRadioButton
        addToExistingRadio.isSelected = true

        val existingClassComboBoxField = dialog.javaClass.getDeclaredField("existingClassComboBox")
        existingClassComboBoxField.isAccessible = true
        val existingClassComboBox = existingClassComboBoxField.get(dialog) as com.intellij.openapi.ui.ComboBox<String>

        // Select TestTarget
        existingClassComboBox.selectedItem = "TestTarget"

        val doValidateMethod = dialog.javaClass.getDeclaredMethod("doValidate")
        doValidateMethod.isAccessible = true
        val validationInfo = doValidateMethod.invoke(dialog) as? com.intellij.openapi.ui.ValidationInfo

        assertNotNull("Should return validation info for collision", validationInfo)
        assertEquals(
            "Class 'TestTarget' already has a method named 'test_duplicate' (or inherits it)",
            validationInfo?.message
        )
        assertTrue("Should be a warning", validationInfo!!.warning)

        // Case 2: No Collision
        existingClassComboBox.selectedItem = "TestSafe"
        val cleanValidation = doValidateMethod.invoke(dialog)
        assertNull("Should not return validation info for safe target", cleanValidation)
    }

    fun testWarnsOnInheritedFunctionCollisionInTargetClass() {
        myFixture.configureByText(
            "test_collision_inheritance.py",
            """
            class Parent:
                def test_duplicate(self):
                    pass

            class TestTarget(Parent):
                pass
            
            class TestSafe:
                pass
            """
        )

        val file = myFixture.file as PyFile
        val classes = file.topLevelClasses
        val targetClass = classes.find { it.name == "TestTarget" }!!
        val safeClass = classes.find { it.name == "TestSafe" }!!

        val existingClasses = listOf(targetClass, safeClass)

        val dialog = WrapTestInClassDialog(
            project,
            "TestNew",
            existingTestClasses = existingClasses,
            sourceFunctionName = "test_duplicate"
        )

        val addToExistingRadioField = dialog.javaClass.getDeclaredField("addToExistingClassRadio")
        addToExistingRadioField.isAccessible = true
        val addToExistingRadio = addToExistingRadioField.get(dialog) as javax.swing.JRadioButton
        addToExistingRadio.isSelected = true

        val existingClassComboBoxField = dialog.javaClass.getDeclaredField("existingClassComboBox")
        existingClassComboBoxField.isAccessible = true
        val existingClassComboBox = existingClassComboBoxField.get(dialog) as com.intellij.openapi.ui.ComboBox<String>

        // Select TestTarget
        existingClassComboBox.selectedItem = "TestTarget"

        val doValidateMethod = dialog.javaClass.getDeclaredMethod("doValidate")
        doValidateMethod.isAccessible = true
        val validationInfo = doValidateMethod.invoke(dialog) as? com.intellij.openapi.ui.ValidationInfo

        assertNotNull("Should return validation info for inherited collision", validationInfo)
        assertEquals(
            "Class 'TestTarget' already has a method named 'test_duplicate' (or inherits it)",
            validationInfo?.message
        )
        assertTrue("Should be a warning", validationInfo!!.warning)
    }

    fun testRejectsInvalidClassName() {
        myFixture.configureByText(
            "test_invalid_name.py",
            """
            class TestSafe:
                pass
            """
        )
        val file = myFixture.file as PyFile
        val existingClasses = file.topLevelClasses

        val dialog = WrapTestInClassDialog(
            project,
            "123Invalid", // Initial invalid name
            existingTestClasses = existingClasses
        )

        // Ensure "Create new class" is selected (default)

        // Verify doValidate rejects it
        val doValidateMethod = dialog.javaClass.getDeclaredMethod("doValidate")
        doValidateMethod.isAccessible = true
        var validationInfo = doValidateMethod.invoke(dialog) as? com.intellij.openapi.ui.ValidationInfo

        assertNotNull("Should reject invalid identifier", validationInfo)
        assertEquals("Class name must be a valid Python identifier", validationInfo?.message)

        // Update text to another invalid name
        val classNameFieldField = dialog.javaClass.getDeclaredField("classNameField")
        classNameFieldField.isAccessible = true
        val classNameField = classNameFieldField.get(dialog) as javax.swing.JTextField

        classNameField.text = "ValidName"
        validationInfo = doValidateMethod.invoke(dialog) as? com.intellij.openapi.ui.ValidationInfo
        assertNull("Should accept valid identifier", validationInfo)

        classNameField.text = "Invalid-Name"
        validationInfo = doValidateMethod.invoke(dialog) as? com.intellij.openapi.ui.ValidationInfo
        assertNotNull("Should reject hyphen", validationInfo)
        assertEquals("Class name must be a valid Python identifier", validationInfo?.message)

        classNameField.text = "Invalid Name"
        validationInfo = doValidateMethod.invoke(dialog) as? com.intellij.openapi.ui.ValidationInfo
        assertNotNull("Should reject space", validationInfo)
    }
}
