package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.refactoring.PyBaseRefactoringAction

class IntroduceParameterObjectRefactoringAction : PyBaseRefactoringAction() {

    override fun isAvailableInEditorOnly(): Boolean {
        return true
    }

    override fun isEnabledOnElementInsideEditor(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        if (!PluginSettingsState.instance().state.enableIntroduceParameterObjectRefactoringAction) return false
        return IntroduceParameterObjectTarget.isAvailable(element)
    }

    override fun isEnabledOnElementsOutsideEditor(elements: Array<out PsiElement>): Boolean {
        return false
    }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return IntroduceParameterObjectRefactoringHandler()
    }
}
