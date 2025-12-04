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
    private val parameters: List<PyNamedParameter>
) : DialogWrapper(project) {

    private val checkBoxList = CheckBoxList<PyNamedParameter>()

    init {
        title = "Introduce Parameter Object"
        init()

        parameters.forEach { param ->
            checkBoxList.addItem(param, param.name ?: "", true)
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("Select parameters to extract:"), BorderLayout.NORTH)
        panel.add(JBScrollPane(checkBoxList), BorderLayout.CENTER)
        return panel
    }

    fun getSelectedParameters(): List<PyNamedParameter> {
        val selected = mutableListOf<PyNamedParameter>()
        for (i in 0 until checkBoxList.itemsCount) {
            val item = checkBoxList.getItemAt(i)
            if (checkBoxList.isItemSelected(i) && item != null) {
                selected.add(item)
            }
        }
        return selected
    }
}
