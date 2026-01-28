package com.github.chbndrhnns.betterpy.features.intentions.populate

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

/**
 * PopupHost implementation backed by the IntelliJ platform UI for populate options.
 */
class JbPopulateOptionsPopupHost : PopulateOptionsPopupHost {
    override fun showOptions(
        editor: Editor,
        title: String,
        recursiveAvailable: Boolean,
        localsAvailable: Boolean,
        initial: PopulateOptions,
        onChosen: (PopulateOptions) -> Unit
    ) {
        val allButton = JBRadioButton("All arguments", initial.mode == PopulateMode.ALL)
        val requiredButton = JBRadioButton(
            "Required arguments only",
            initial.mode == PopulateMode.REQUIRED_ONLY
        )
        val modeGroup = ButtonGroup()
        modeGroup.add(allButton)
        modeGroup.add(requiredButton)

        val recursiveBox = JBCheckBox("Recursive", initial.recursive).apply {
            isEnabled = recursiveAvailable
            horizontalAlignment = SwingConstants.LEFT
        }
        val localsBox = JBCheckBox("From locals", initial.useLocalScope).apply {
            isEnabled = localsAvailable
            horizontalAlignment = SwingConstants.LEFT
        }

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(allButton)
            add(requiredButton)
            add(recursiveBox)
            add(localsBox)
        }

        val populateButton = JButton("Populate")
        val cancelButton = JButton("Cancel")
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(cancelButton)
            add(populateButton)
        }

        val wrapper = JPanel(BorderLayout()).apply {
            add(content, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(wrapper, allButton)
            .setTitle(title)
            .setMovable(true)
            .setResizable(false)
            .setRequestFocus(true)
            .createPopup()

        populateButton.addActionListener {
            val mode = if (allButton.isSelected) PopulateMode.ALL else PopulateMode.REQUIRED_ONLY
            val chosen = PopulateOptions(
                mode = mode,
                recursive = recursiveBox.isSelected && recursiveAvailable,
                useLocalScope = localsBox.isSelected && localsAvailable
            )
            onChosen(chosen)
            popup.closeOk(null)
        }
        cancelButton.addActionListener { popup.cancel() }

        popup.showInBestPositionFor(editor)
    }
}
