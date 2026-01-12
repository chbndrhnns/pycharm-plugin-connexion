package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

class WrapTestInClassDialog(
    project: Project,
    suggestedClassName: String,
    dialogTitle: String = "Wrap Test in Class",
    private val existingTestClasses: List<PyClass>,
    private val sourceFunctionName: String? = null
) : DialogWrapper(project) {

    private val classNameField = JTextField(suggestedClassName)
    private val createNewClassRadio = JRadioButton("Create new class", true)
    private val addToExistingClassRadio = JRadioButton("Add to existing class", false)
    private val existingClassComboBox = ComboBox<String>()

    init {
        title = dialogTitle
        init()

        // Setup radio button group
        val buttonGroup = ButtonGroup()
        buttonGroup.add(createNewClassRadio)
        buttonGroup.add(addToExistingClassRadio)

        // Populate existing classes combo box
        existingTestClasses.forEach { pyClass ->
            existingClassComboBox.addItem(pyClass.name ?: "")
        }

        // Enable/disable controls based on radio selection
        updateControlStates()

        createNewClassRadio.addActionListener { updateControlStates() }
        addToExistingClassRadio.addActionListener { updateControlStates() }

        // Disable "add to existing" if no existing classes
        if (existingTestClasses.isEmpty()) {
            addToExistingClassRadio.isEnabled = false
        }
    }

    private fun updateControlStates() {
        classNameField.isEnabled = createNewClassRadio.isSelected
        existingClassComboBox.isEnabled = addToExistingClassRadio.isSelected
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val optionsPanel = JPanel(GridLayout(4, 1))
        optionsPanel.add(createNewClassRadio)

        val classNamePanel = JPanel(BorderLayout())
        classNamePanel.add(Box.createHorizontalStrut(20), BorderLayout.WEST)
        val classNameFieldPanel = JPanel(BorderLayout())
        classNameFieldPanel.add(JLabel("Class name: "), BorderLayout.WEST)
        classNameFieldPanel.add(classNameField, BorderLayout.CENTER)
        classNamePanel.add(classNameFieldPanel, BorderLayout.CENTER)
        optionsPanel.add(classNamePanel)

        optionsPanel.add(addToExistingClassRadio)

        // Existing class combo (indented)
        val existingClassPanel = JPanel(BorderLayout())
        existingClassPanel.add(Box.createHorizontalStrut(20), BorderLayout.WEST)
        val comboPanel = JPanel(BorderLayout())
        comboPanel.add(JLabel("Select class: "), BorderLayout.WEST)
        comboPanel.add(existingClassComboBox, BorderLayout.CENTER)
        existingClassPanel.add(comboPanel, BorderLayout.CENTER)
        optionsPanel.add(existingClassPanel)

        panel.add(optionsPanel, BorderLayout.CENTER)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (createNewClassRadio.isSelected) {
            val name = classNameField.text.trim()
            if (name.isEmpty()) {
                return ValidationInfo("Class name cannot be empty", classNameField)
            }
            if (!name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) {
                return ValidationInfo("Class name must be a valid Python identifier", classNameField)
            }
            // Check for existing class name
            val existingNames = existingTestClasses.mapNotNull { it.name }
            if (name in existingNames) {
                return ValidationInfo("Class '$name' already exists", classNameField).asWarning()
            }
        } else if (addToExistingClassRadio.isSelected) {
            val selectedIndex = existingClassComboBox.selectedIndex
            if (selectedIndex >= 0 && selectedIndex < existingTestClasses.size) {
                val targetClass = existingTestClasses[selectedIndex]
                val functionName = sourceFunctionName
                if (functionName != null && targetClass.findMethodByName(functionName, true, null) != null) {
                    return ValidationInfo(
                        "Class '${targetClass.name}' already has a method named '$functionName' (or inherits it)",
                        existingClassComboBox
                    ).asWarning()
                }
            }
        }
        return super.doValidate()
    }

    fun getSettings(): WrapTestInClassSettings {
        return if (createNewClassRadio.isSelected) {
            WrapTestInClassSettings.CreateNewClass(classNameField.text)
        } else {
            val selectedIndex = existingClassComboBox.selectedIndex
            if (selectedIndex >= 0 && selectedIndex < existingTestClasses.size) {
                WrapTestInClassSettings.AddToExistingClass(existingTestClasses[selectedIndex])
            } else {
                // Fallback to creating new class if selection is invalid
                WrapTestInClassSettings.CreateNewClass(classNameField.text)
            }
        }
    }
}

sealed class WrapTestInClassSettings {
    data class CreateNewClass(val className: String) : WrapTestInClassSettings()
    data class AddToExistingClass(val targetClass: PyClass) : WrapTestInClassSettings()
}

/**
 * Find all test classes in the given PyFile.
 * A test class is one whose name starts with "Test" or ends with "Test".
 */
fun findTestClassesInFile(file: PyFile): List<PyClass> {
    return file.topLevelClasses.filter { pyClass ->
        val name = pyClass.name ?: return@filter false
        name.startsWith("Test") || name.endsWith("Test")
    }
}
