package com.github.chbndrhnns.betterpy.features.pytest.extractfixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.refactoring.PyBaseRefactoringAction

class ExtractFixtureRefactoringAction : PyBaseRefactoringAction() {

    override fun isAvailableInEditorOnly(): Boolean = true

    override fun isEnabledOnElementInsideEditor(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        if (!PluginSettingsState.instance().state.enableExtractPytestFixtureRefactoring) return false
        if (file !is PyFile) return false

        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false
        return isTestOrFixtureFunction(function)
    }

    override fun isEnabledOnElementsOutsideEditor(elements: Array<out PsiElement>): Boolean = false

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return ExtractFixtureRefactoringHandler()
    }

    private fun isTestOrFixtureFunction(function: PyFunction): Boolean {
        val name = function.name ?: return false
        if (name.startsWith("test_") || name.startsWith("test")) return true

        val decorators = function.decoratorList?.decorators ?: return false
        return decorators.any { decorator ->
            val callee = decorator.callee?.text ?: decorator.name
            callee == "pytest.fixture" || callee == "fixture"
        }
    }
}
