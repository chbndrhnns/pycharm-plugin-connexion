package com.github.chbndrhnns.betterpy.features.pytest.extractfixture

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFile

class ExtractFixtureRefactoringHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is PyFile) return

        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            // TODO: Show error message
            return
        }

        val analyzer = ExtractFixtureAnalyzer(file, selectionModel)
        val model = analyzer.analyze()
        if (model == null) {
            // TODO: Show error message
            return
        }

        val suggestedName = suggestFixtureName(model)
        val dialog = ExtractFixtureDialog(project, model, suggestedName)
        if (!dialog.showAndGet()) return

        val options = dialog.getOptions()
        
        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Not supported from project view
    }

    private fun suggestFixtureName(model: ExtractFixtureModel): String {
        // Try to derive name from output variables or defined locals
        val outputVar = model.outputVariables.firstOrNull()
        if (outputVar != null) {
            return outputVar
        }

        val definedLocal = model.definedLocals.firstOrNull()
        if (definedLocal != null) {
            return definedLocal
        }

        return "extracted_fixture"
    }
}
