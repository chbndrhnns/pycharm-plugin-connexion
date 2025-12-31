package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.refactoring.PyBaseRefactoringAction

class WrapTestInClassRefactoringAction : PyBaseRefactoringAction() {

    override fun isAvailableInEditorOnly(): Boolean {
        return true
    }

    override fun isEnabledOnElementInsideEditor(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        if (!PluginSettingsState.instance().state.enableWrapTestInClassIntention) return false
        if (file !is PyFile) return false

        // `PyBaseRefactoringAction` may provide a *resolved* target element (e.g. built-in `int` from library)
        // when the caret is on a type annotation. For availability we must prefer the leaf PSI at the caret,
        // just like the refactoring handler does.
        val leafAtCaret = file.findElementAt(editor.caretModel.offset) ?: return false
        val function = PsiTreeUtil.getParentOfType(leafAtCaret, PyFunction::class.java) ?: return false

        // Check if it's a module-level test function (not inside a class)
        val parentClass = PsiTreeUtil.getParentOfType(function, PyClass::class.java)
        if (parentClass != null) return false

        // Check if it's a test function
        val name = function.name ?: return false
        return name.startsWith("test_")
    }

    override fun isEnabledOnElementsOutsideEditor(elements: Array<out PsiElement>): Boolean {
        return false
    }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return WrapTestInClassRefactoringHandler()
    }
}
