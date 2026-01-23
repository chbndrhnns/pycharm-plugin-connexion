package com.github.chbndrhnns.betterpy.features.intentions.customtype

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyBaseRefactoringAction

class IntroduceCustomTypeRefactoringAction : PyBaseRefactoringAction() {

    private val planBuilder = PlanBuilder()

    override fun isAvailableInEditorOnly(): Boolean {
        return true
    }

    override fun isEnabledOnElementInsideEditor(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        if (!PluginSettingsState.instance().state.enableIntroduceCustomTypeRefactoringAction) return false

        val vFile = file.virtualFile ?: return false
        if (!ProjectFileIndex.getInstance(file.project).isInContent(vFile)) {
            return false
        }

        val pyFile = file as? PyFile ?: return false

        // Disable when the file contains parse errors
        if (PsiTreeUtil.hasErrorElements(pyFile)) {
            return false
        }

        val typeContext = TypeEvalContext.codeAnalysis(file.project, file)
        val plan = planBuilder.build(editor, pyFile, typeContext)
        return plan != null
    }

    override fun isEnabledOnElementsOutsideEditor(elements: Array<out PsiElement>): Boolean {
        return false
    }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return IntroduceCustomTypeRefactoringHandler()
    }
}
