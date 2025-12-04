package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.python.psi.PyNamedParameter
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class IntroduceParameterObjectDialog(
    project: Project,
    parameters: List<PyNamedParameter>,
    defaultClassName: String
) : DialogWrapper(project) {

    private val checkBoxList = CheckBoxList<PyNamedParameter>()
    private val classNameField = javax.swing.JTextField(defaultClassName)
    private val parameterNameField = javax.swing.JTextField("params")
    private val frozenCheckBox = javax.swing.JCheckBox("Frozen", true)
    private val slotsCheckBox = javax.swing.JCheckBox("Slots", true)
    private val kwOnlyCheckBox = javax.swing.JCheckBox("kw_only", true)

    init {
        title = "Introduce Parameter Object"
        init()

        parameters.forEach { param ->
            checkBoxList.addItem(param, param.name ?: "", true)
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val configPanel = JPanel(java.awt.GridLayout(4, 2))
        configPanel.add(JLabel("Class Name:"))
        configPanel.add(classNameField)
        configPanel.add(JLabel("Parameter Name:"))
        configPanel.add(parameterNameField)
        configPanel.add(JLabel("Dataclass Options:"))
        val optionsPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))
        optionsPanel.add(frozenCheckBox)
        optionsPanel.add(slotsCheckBox)
        optionsPanel.add(kwOnlyCheckBox)
        configPanel.add(optionsPanel)

        panel.add(configPanel, BorderLayout.NORTH)
        panel.add(JBScrollPane(checkBoxList), BorderLayout.CENTER)
        return panel
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
            generateFrozen = frozenCheckBox.isSelected,
            generateSlots = slotsCheckBox.isSelected,
            generateKwOnly = kwOnlyCheckBox.isSelected
        )
    }
}
