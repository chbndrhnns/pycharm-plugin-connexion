package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.core.MyBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler

class InlineParameterObjectRefactoringHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val function = InlineParameterObjectTarget.find(element) ?: return

        // Count usages to determine if we need to show the dialog
        val processor = PyInlineParameterObjectProcessor(function, element)
        val usageCount = runWithModalProgressBlocking(
            project,
            MyBundle.message("inline.parameter.object.progress.counting.usages")
        ) {
            readAction {
                processor.countUsages()
            }
        }

        val hasUnsupportedTypeHints = runWithModalProgressBlocking(
            project,
            MyBundle.message("inline.parameter.object.progress.checking.type.hints")
        ) {
            readAction {
                processor.hasUnsupportedTypeHintUsages()
            }
        }

        if (hasUnsupportedTypeHints) {
            Messages.showErrorDialog(
                project,
                MyBundle.message("inline.parameter.object.blocked.type.hints"),
                MyBundle.message("inline.parameter.object.title")
            )
            return
        }

        if (usageCount > 1) {
            // Show dialog to get user preferences
            val dialog = InlineParameterObjectDialog(project, usageCount)
            if (dialog.showAndGet()) {
                val settings = dialog.getSettings()
                processor.run(settings)
            }
        } else {
            // Single usage or no usages - use default settings (inline all, remove class if unused)
            processor.run(InlineParameterObjectSettings(inlineAllOccurrences = true, removeClass = true))
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Not supported from project view
    }
}
