package com.github.chbndrhnns.betterpy.features.refactoring.move.ui

import com.github.chbndrhnns.betterpy.features.refactoring.move.PyMoveModel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import javax.swing.JCheckBox
import javax.swing.JComponent

class PyMoveDialog(
    project: Project,
    private val elements: List<com.intellij.psi.PsiElement>,
    private val onSuccess: (() -> Unit)? = null
) : RefactoringDialog(project, false) {

    private val modulePathProvider = PyModulePathCompletionProvider(project)
    internal val modulePathField: TextFieldWithAutoCompletion<String> = TextFieldWithAutoCompletion(
        project,
        modulePathProvider,
        true,
        ""
    )
    private val searchReferencesCheckbox = JCheckBox("Search for references", true)

    init {
        title = "Move"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val listModel = elements.mapNotNull { renderElementLabel(it) }
        val list = JBList(listModel)
        list.visibleRowCount = minOf(4, listModel.size.coerceAtLeast(1))

        return panel {
            row("Members to move:") {}
            row {
                cell(ScrollPaneFactory.createScrollPane(list))
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()

            row("To module:") {
                cell(modulePathField)
                    .align(Align.FILL)
                    .resizableColumn()
            }

            row {
                cell(searchReferencesCheckbox)
            }
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = modulePathField

    override fun doValidate(): ValidationInfo? {
        val trimmed = modulePathField.text.trim()
        if (trimmed.isEmpty()) {
            return ValidationInfo("Target module path is required", modulePathField)
        }
        if (!isValidModulePath(trimmed)) {
            return ValidationInfo("Target module path must be a dotted Python module path", modulePathField)
        }
        return null
    }

    override fun canRun() {
        val trimmed = modulePathField.text.trim()
        if (trimmed.isEmpty()) {
            throw ConfigurationException("Target module path is required")
        }
        if (!isValidModulePath(trimmed)) {
            throw ConfigurationException("Target module path must be a dotted Python module path")
        }
    }

    override fun hasPreviewButton(): Boolean = false

    override fun doAction() {
        val modulePath = modulePathField.text.trim()
        val model = PyMoveModel(
            elements = elements,
            targetModulePath = modulePath,
            searchReferences = searchReferencesCheckbox.isSelected
        )
        if (!model.isValidRefactoring()) return

        model.toDescriptor().run(myProject)
        onSuccess?.invoke()
        closeOKAction()
    }

    private fun renderElementLabel(element: com.intellij.psi.PsiElement): String? {
        return when (element) {
            is PyFunction -> element.name?.let { "def $it" }
            is PyClass -> element.name?.let { "class $it" }
            else -> null
        }
    }

    private fun isValidModulePath(path: String): Boolean {
        return MODULE_PATH_REGEX.matches(path)
    }

    companion object {
        private val MODULE_PATH_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")
    }
}
