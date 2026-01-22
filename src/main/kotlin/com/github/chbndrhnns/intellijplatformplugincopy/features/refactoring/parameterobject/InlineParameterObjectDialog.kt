package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.core.MyBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

class InlineParameterObjectDialog(
    project: Project,
    private val usageCount: Int
) : DialogWrapper(project) {

    private val inlineThisOccurrenceRadio = JRadioButton(
        MyBundle.message("inline.parameter.object.dialog.option.this"),
        false
    )
    private val inlineAllOccurrencesRadio = JRadioButton(
        MyBundle.message("inline.parameter.object.dialog.option.all", usageCount),
        true
    )
    private val removeClassCheckBox = JCheckBox(
        MyBundle.message("inline.parameter.object.dialog.option.remove.class"),
        true
    )

    init {
        title = MyBundle.message("inline.parameter.object.title")
        init()

        // Setup radio button group
        val buttonGroup = ButtonGroup()
        buttonGroup.add(inlineThisOccurrenceRadio)
        buttonGroup.add(inlineAllOccurrencesRadio)

        if (usageCount <= 1) {
            inlineThisOccurrenceRadio.isEnabled = false
        }

        inlineThisOccurrenceRadio.addActionListener {
            updateCheckboxState()
        }
        inlineAllOccurrencesRadio.addActionListener {
            updateCheckboxState()
        }

        updateCheckboxState()
    }

    private fun updateCheckboxState() {
        // Disable checkbox when "inline this occurrence only" is selected
        removeClassCheckBox.isEnabled = inlineAllOccurrencesRadio.isSelected
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val optionsPanel = JPanel(GridLayout(3, 1))

        optionsPanel.add(inlineAllOccurrencesRadio)
        optionsPanel.add(inlineThisOccurrenceRadio)
        optionsPanel.add(removeClassCheckBox)

        panel.add(optionsPanel, BorderLayout.CENTER)
        return panel
    }

    fun getSettings(): InlineParameterObjectSettings {
        return InlineParameterObjectSettings(
            inlineAllOccurrences = inlineAllOccurrencesRadio.isSelected,
            removeClass = removeClassCheckBox.isSelected
        )
    }
}

data class InlineParameterObjectSettings(
    val inlineAllOccurrences: Boolean,
    val removeClass: Boolean
)
