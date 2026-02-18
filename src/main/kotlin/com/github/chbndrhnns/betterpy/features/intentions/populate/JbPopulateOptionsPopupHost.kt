package com.github.chbndrhnns.betterpy.features.intentions.populate

import com.intellij.openapi.application.runReadAction
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
        previewProvider: (PopulateOptions) -> String?,
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
        val constructorsBox = JBCheckBox("Use constructors", initial.useConstructors).apply {
            horizontalAlignment = SwingConstants.LEFT
        }

        val previewArea = com.intellij.ui.components.JBTextArea(4, 40).apply {
            isEditable = false
            font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        }
        val previewPanel = JPanel(BorderLayout()).apply {
            add(javax.swing.JLabel("Preview:"), BorderLayout.NORTH)
            add(javax.swing.JScrollPane(previewArea), BorderLayout.CENTER)
        }

        fun currentOptions(): PopulateOptions {
            val mode = if (allButton.isSelected) PopulateMode.ALL else PopulateMode.REQUIRED_ONLY
            return PopulateOptions(
                mode = mode,
                recursive = recursiveBox.isSelected && recursiveAvailable,
                useLocalScope = localsBox.isSelected && localsAvailable,
                useConstructors = constructorsBox.isSelected
            )
        }

        fun updatePreview() {
            val preview = runReadAction { previewProvider(currentOptions()) }
            previewArea.text = preview ?: "No preview available."
        }

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(allButton)
            add(requiredButton)
            add(recursiveBox)
            add(localsBox)
            add(constructorsBox)
        }

        val populateButton = JButton("Populate")
        val cancelButton = JButton("Cancel")
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(cancelButton)
            add(populateButton)
        }

        val wrapper = JPanel(BorderLayout()).apply {
            add(content, BorderLayout.NORTH)
            add(previewPanel, BorderLayout.CENTER)
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
            onChosen(currentOptions())
            popup.closeOk(null)
        }
        cancelButton.addActionListener { popup.cancel() }

        val updateListener = java.awt.event.ActionListener { updatePreview() }
        allButton.addActionListener(updateListener)
        requiredButton.addActionListener(updateListener)
        recursiveBox.addActionListener(updateListener)
        localsBox.addActionListener(updateListener)
        constructorsBox.addActionListener(updateListener)

        updatePreview()
        popup.showInBestPositionFor(editor)
    }
}
