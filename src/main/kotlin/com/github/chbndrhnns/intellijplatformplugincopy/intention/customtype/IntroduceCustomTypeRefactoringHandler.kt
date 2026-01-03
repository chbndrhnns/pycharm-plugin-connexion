package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContext

class IntroduceCustomTypeRefactoringHandler : RefactoringActionHandler {

    private val planBuilder = PlanBuilder()
    private val naming = NameSuggester()
    private val applier = CustomTypeApplier()

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val pyFile = file as? PyFile ?: return
        val context = TypeEvalContext.codeAnalysis(project, file)
        val basePlan = planBuilder.build(editor, pyFile, context) ?: return

        // In headless mode (tests), skip dialog and use defaults
        // Keep preferredClassName as-is so that inline rename still triggers when expected
        val plan = if (ApplicationManager.getApplication().isHeadlessEnvironment) {
            basePlan
        } else {
            val suggestedName = naming.suggestTypeName(basePlan.builtinName, basePlan.preferredClassName)
            // Show dialog to let user choose type kind and confirm/edit class name
            val dialog = IntroduceCustomTypeDialog(project, suggestedName, basePlan.builtinName)

            if (!dialog.showAndGet()) {
                return // User cancelled
            }

            val settings = dialog.getSettings()

            // Update plan with user's selections
            basePlan.copy(
                typeKind = settings.typeKind,
                preferredClassName = settings.className
            )
        }

        applier.apply(project, editor, plan, isPreview = false)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Not supported from project view
    }
}
