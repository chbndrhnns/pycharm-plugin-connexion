package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

/**
 * Dialog for selecting the type of custom type to introduce.
 *
 * Allows the user to choose between:
 * - Subclass (default): `class ProductId(int): pass`
 * - NewType: `ProductId = NewType("ProductId", int)`
 * - Frozen Dataclass: `@dataclass(frozen=True) class ProductId: value: int`
 * - Pydantic Value Object: `class ProductId(BaseModel): value: int`
 */
class IntroduceCustomTypeDialog(
    project: Project,
    private val suggestedName: String,
    private val builtinName: String
) : DialogWrapper(project) {

    private val classNameField = JTextField(suggestedName)
    private val typeKindComboBox = JComboBox(DefaultComboBoxModel(CustomTypeKind.entries.toTypedArray()))
    private val previewArea = JTextArea(4, 40)

    init {
        title = "Introduce Custom Type"
        init()

        // Set default type kind
        typeKindComboBox.selectedItem = CustomTypeKind.DEFAULT

        // Update preview when type kind changes
        typeKindComboBox.addActionListener { updatePreview() }
        classNameField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updatePreview()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updatePreview()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updatePreview()
        })

        // Initial preview
        updatePreview()
    }

    private fun updatePreview() {
        val name = classNameField.text.ifBlank { "CustomType" }
        val kind = typeKindComboBox.selectedItem as? CustomTypeKind ?: CustomTypeKind.DEFAULT

        val preview = when (kind) {
            CustomTypeKind.SUBCLASS -> {
                val body = if (builtinName == "str") "__slots__ = ()" else "pass"
                "class $name($builtinName):\n    $body"
            }

            CustomTypeKind.NEWTYPE -> {
                "$name = NewType(\"$name\", $builtinName)"
            }

            CustomTypeKind.FROZEN_DATACLASS -> {
                "@dataclass(frozen=True)\nclass $name:\n    value: $builtinName"
            }

            CustomTypeKind.PYDANTIC_VALUE_OBJECT -> {
                "class $name(BaseModel):\n    value: $builtinName\n    model_config = ConfigDict(frozen=True)"
            }
        }
        previewArea.text = preview
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))

        // Configuration panel
        val configPanel = JPanel(GridLayout(2, 2, 5, 5))
        configPanel.add(JLabel("Class Name:"))
        configPanel.add(classNameField)
        configPanel.add(JLabel("Type Kind:"))
        configPanel.add(typeKindComboBox)

        // Preview panel
        val previewPanel = JPanel(BorderLayout())
        previewPanel.add(JLabel("Preview:"), BorderLayout.NORTH)
        previewArea.isEditable = false
        previewArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        previewPanel.add(JScrollPane(previewArea), BorderLayout.CENTER)

        panel.add(configPanel, BorderLayout.NORTH)
        panel.add(previewPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * Returns the settings selected by the user.
     */
    fun getSettings(): IntroduceCustomTypeSettings {
        return IntroduceCustomTypeSettings(
            className = classNameField.text.ifBlank { suggestedName },
            typeKind = typeKindComboBox.selectedItem as? CustomTypeKind ?: CustomTypeKind.DEFAULT
        )
    }
}

/**
 * Settings returned from the IntroduceCustomTypeDialog.
 */
data class IntroduceCustomTypeSettings(
    val className: String,
    val typeKind: CustomTypeKind
)
