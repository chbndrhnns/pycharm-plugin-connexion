package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler

class IntroduceParameterObjectRefactoringHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val function = IntroduceParameterObjectTarget.find(element) ?: return

        PyIntroduceParameterObjectProcessor(function).run()
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Not supported from project view
    }
}
