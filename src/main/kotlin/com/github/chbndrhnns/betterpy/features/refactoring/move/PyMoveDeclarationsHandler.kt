package com.github.chbndrhnns.betterpy.features.refactoring.move

import com.github.chbndrhnns.betterpy.features.refactoring.move.ui.PyMoveDialog
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.jetbrains.python.PythonLanguage

class PyMoveDeclarationsHandler : MoveHandlerDelegate() {

    override fun supportsLanguage(language: Language): Boolean {
        return language.isKindOf(PythonLanguage.getInstance())
    }

    override fun canMove(
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        reference: PsiReference?
    ): Boolean {
        return elements.any { PyMoveDeclarationsTarget.isMovableElement(it) }
    }

    override fun canMove(dataContext: DataContext): Boolean {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        return PyMoveDeclarationsTarget.findElementToMove(element, editor) != null
    }

    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext,
        reference: PsiReference?,
        editor: Editor?
    ): Boolean {
        val target = PyMoveDeclarationsTarget.findElementToMove(element, editor) ?: return false
        PyMoveDialog(project, listOf(target)).show()
        return true
    }

    override fun doMove(
        project: Project,
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        callback: MoveCallback?
    ) {
        val candidates = elements.filter { PyMoveDeclarationsTarget.isMovableElement(it) }
        if (candidates.isEmpty()) return
        PyMoveDialog(project, candidates) {
            callback?.refactoringCompleted()
        }.show()
    }

    override fun getActionName(elements: Array<out PsiElement>): String = "Move"
}
