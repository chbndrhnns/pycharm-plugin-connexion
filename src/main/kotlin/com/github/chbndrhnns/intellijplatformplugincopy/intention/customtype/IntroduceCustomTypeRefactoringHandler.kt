package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContext

class IntroduceCustomTypeRefactoringHandler : RefactoringActionHandler {

    private val planBuilder = PlanBuilder()
    private val applier = CustomTypeApplier()

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val pyFile = file as? PyFile ?: return
        val context = TypeEvalContext.codeAnalysis(project, file)
        val plan = planBuilder.build(editor, pyFile, context) ?: return

        applier.apply(project, editor, plan, isPreview = false)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Not supported from project view
    }
}
