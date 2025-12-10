package com.github.chbndrhnns.intellijplatformplugincopy.refactoring

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
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
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false
        
        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }
        
        return parameters.size >= 2
    }

    override fun isEnabledOnElementsOutsideEditor(elements: Array<out PsiElement>): Boolean {
        return false
    }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return IntroduceParameterObjectRefactoringHandler()
    }
}
