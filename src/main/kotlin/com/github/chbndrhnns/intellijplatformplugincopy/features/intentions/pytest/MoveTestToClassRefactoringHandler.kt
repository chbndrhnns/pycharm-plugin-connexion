package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFile

class MoveTestToClassRefactoringHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is PyFile) return

        val intention = MoveTestToClassIntention()
        if (intention.isAvailable(project, editor, file)) {
            intention.invoke(project, editor, file)
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Not supported from project view
    }
}
