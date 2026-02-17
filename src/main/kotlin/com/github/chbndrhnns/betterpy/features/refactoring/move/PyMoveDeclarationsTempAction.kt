package com.github.chbndrhnns.betterpy.features.refactoring.move

import com.github.chbndrhnns.betterpy.features.refactoring.move.ui.PyMoveDialog
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.refactoring.PyBaseRefactoringAction

class PyMoveDeclarationsTempAction : PyBaseRefactoringAction() {

    override fun isAvailableInEditorOnly(): Boolean = true

    override fun isEnabledOnElementInsideEditor(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        val leaf = file.findElementAt(editor.caretModel.offset) ?: return false
        return PyMoveDeclarationsTarget.findElementToMove(leaf, editor) != null
    }

    override fun isEnabledOnElementsOutsideEditor(elements: Array<out PsiElement>): Boolean = false

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return PyMoveDeclarationsTempHandler()
    }
}

private class PyMoveDeclarationsTempHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val leaf = file.findElementAt(editor.caretModel.offset) ?: return
        val target = PyMoveDeclarationsTarget.findElementToMove(leaf, editor) ?: return
        PyMoveDialog(project, listOf(target)).show()
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Not supported from project view.
    }
}
