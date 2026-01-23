package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.refactoring.PyBaseRefactoringAction

class MoveTestToClassRefactoringAction : PyBaseRefactoringAction() {

    override fun isAvailableInEditorOnly(): Boolean = true

    override fun isEnabledOnElementInsideEditor(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        val intention = MoveTestToClassIntention()
        return intention.isAvailable(element.project, editor, file)
    }

    override fun isEnabledOnElementsOutsideEditor(elements: Array<out PsiElement>): Boolean = false

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return MoveTestToClassRefactoringHandler()
    }
}
