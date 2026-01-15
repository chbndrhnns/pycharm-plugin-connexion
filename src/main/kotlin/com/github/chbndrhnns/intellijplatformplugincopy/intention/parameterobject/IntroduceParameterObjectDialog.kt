package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.python.psi.PyNamedParameter
import java.awt.BorderLayout
import javax.swing.*

class IntroduceParameterObjectDialog(
    project: Project,
    parameters: List<PyNamedParameter>,
    defaultClassName: String
) : DialogWrapper(project) {

    private val checkBoxList = CheckBoxList<PyNamedParameter>()
    private val classNameField = JTextField(defaultClassName)
    private val parameterNameField = JTextField(IntroduceParameterObjectSettings.DEFAULT_PARAMETER_NAME)
    private val baseTypeComboBox = JComboBox(DefaultComboBoxModel(ParameterObjectBaseType.entries.toTypedArray()))
    private val frozenCheckBox = JCheckBox("Frozen", true)
    private val slotsCheckBox = JCheckBox("Slots", true)
    private val kwOnlyCheckBox = JCheckBox("kw_only", true)

    private val validator = if (parameters.isNotEmpty()) {
        IntroduceParameterObjectValidator(project, parameters.first())
    } else {
        null
    }

    init {
        title = "Introduce Parameter Object"
        init()

        // Set default base type from global settings
        val defaultBaseType = ParameterObjectBaseType.fromDisplayName(
            PluginSettingsState.instance().state.defaultParameterObjectBaseType
        )
        baseTypeComboBox.selectedItem = defaultBaseType
        updateOptionsForBaseType(defaultBaseType)

        // Add listener to update options when base type changes
        baseTypeComboBox.addActionListener {
            val selectedType = baseTypeComboBox.selectedItem as? ParameterObjectBaseType
            if (selectedType != null) {
                updateOptionsForBaseType(selectedType)
            }
        }

        parameters.forEach { param ->
            checkBoxList.addItem(param, param.name ?: "", true)
        }
    }

    private fun updateOptionsForBaseType(baseType: ParameterObjectBaseType) {
        // Enable/disable options based on base type applicability
        when (baseType) {
            ParameterObjectBaseType.DATACLASS -> {
                frozenCheckBox.isEnabled = true
                slotsCheckBox.isEnabled = true
                kwOnlyCheckBox.isEnabled = true
            }

            ParameterObjectBaseType.NAMED_TUPLE -> {
                // NamedTuple is always immutable, no slots, no kw_only
                frozenCheckBox.isEnabled = false
                frozenCheckBox.isSelected = false
                slotsCheckBox.isEnabled = false
                slotsCheckBox.isSelected = false
                kwOnlyCheckBox.isEnabled = false
                kwOnlyCheckBox.isSelected = false
            }

            ParameterObjectBaseType.TYPED_DICT -> {
                // TypedDict has no frozen, slots, or kw_only options
                frozenCheckBox.isEnabled = false
                frozenCheckBox.isSelected = false
                slotsCheckBox.isEnabled = false
                slotsCheckBox.isSelected = false
                kwOnlyCheckBox.isEnabled = false
                kwOnlyCheckBox.isSelected = false
            }

            ParameterObjectBaseType.PYDANTIC_BASE_MODEL -> {
                // Pydantic supports frozen via Config, no slots in v1, kw_only is default
                frozenCheckBox.isEnabled = true
                slotsCheckBox.isEnabled = false
                slotsCheckBox.isSelected = false
                kwOnlyCheckBox.isEnabled = false
                kwOnlyCheckBox.isSelected = false
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val configPanel = JPanel(java.awt.GridLayout(5, 2))
        configPanel.add(JLabel("Class Name:"))
        configPanel.add(classNameField)
        configPanel.add(JLabel("Parameter Name:"))
        configPanel.add(parameterNameField)
        configPanel.add(JLabel("Base Type:"))
        configPanel.add(baseTypeComboBox)
        configPanel.add(JLabel("Options:"))
        val optionsPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))
        optionsPanel.add(frozenCheckBox)
        optionsPanel.add(slotsCheckBox)
        optionsPanel.add(kwOnlyCheckBox)
        configPanel.add(optionsPanel)

        panel.add(configPanel, BorderLayout.NORTH)
        panel.add(JBScrollPane(checkBoxList), BorderLayout.CENTER)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = classNameField

    override fun doValidate(): ValidationInfo? {
        if (validator == null) return null

        val selected = mutableListOf<PyNamedParameter>()
        for (i in 0 until checkBoxList.itemsCount) {
            val item = checkBoxList.getItemAt(i)
            if (checkBoxList.isItemSelected(i) && item != null) {
                selected.add(item)
            }
        }

        return validator.validate(
            className = classNameField.text,
            parameterName = parameterNameField.text,
            selectedParameters = selected
        )
    }

    fun getSettings(): IntroduceParameterObjectSettings {
        val selected = mutableListOf<PyNamedParameter>()
        for (i in 0 until checkBoxList.itemsCount) {
            val item = checkBoxList.getItemAt(i)
            if (checkBoxList.isItemSelected(i) && item != null) {
                selected.add(item)
            }
        }

        return IntroduceParameterObjectSettings(
            selectedParameters = selected,
            className = classNameField.text,
            parameterName = parameterNameField.text,
            baseType = baseTypeComboBox.selectedItem as? ParameterObjectBaseType ?: ParameterObjectBaseType.DATACLASS,
            generateFrozen = frozenCheckBox.isSelected,
            generateSlots = slotsCheckBox.isSelected,
            generateKwOnly = kwOnlyCheckBox.isSelected
        )
    }

    /**
     * For testing purposes.
     */
    fun setBaseType(baseType: ParameterObjectBaseType) {
        baseTypeComboBox.selectedItem = baseType
    }
}
