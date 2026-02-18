package com.github.chbndrhnns.betterpy.features.pytest.inlinefixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.refactoring.PyBaseRefactoringAction

class InlineFixtureRefactoringAction : PyBaseRefactoringAction() {

    override fun isAvailableInEditorOnly(): Boolean = true

    override fun isEnabledOnElementInsideEditor(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        if (!PluginSettingsState.instance().state.enableInlinePytestFixtureRefactoring) return false
        if (file !is PyFile) return false

        val parameter = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java, false)
        if (parameter != null && isFixtureParameter(parameter)) return true

        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
        if (function != null && PytestFixtureUtil.isFixtureFunction(function)) return true

        return false
    }

    override fun isEnabledOnElementsOutsideEditor(elements: Array<out PsiElement>): Boolean = false

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return InlineFixtureRefactoringHandler()
    }

    private fun isFixtureParameter(parameter: PyNamedParameter): Boolean {
        val name = parameter.name ?: return false
        if (name == "self" || name == "cls") return false
        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return false
        return PytestNaming.isTestFunction(function) || PytestFixtureUtil.isFixtureFunction(function)
    }
}
