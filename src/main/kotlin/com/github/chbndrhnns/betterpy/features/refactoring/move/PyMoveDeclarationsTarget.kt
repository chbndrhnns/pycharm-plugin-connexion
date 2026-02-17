package com.github.chbndrhnns.betterpy.features.refactoring.move

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction

object PyMoveDeclarationsTarget {

    fun isMovableElement(element: PsiElement): Boolean {
        return when (element) {
            is PyFunction -> PsiTreeUtil.getParentOfType(element, PyClass::class.java) == null
            is PyClass -> PsiTreeUtil.getParentOfType(element, PyClass::class.java, true) == null
            else -> false
        }
    }

    fun findElementToMove(element: PsiElement?, editor: Editor?): PsiElement? {
        val leaf = when {
            element == null -> return null
            element is PsiWhiteSpace ->
                PsiTreeUtil.prevVisibleLeaf(element) ?: PsiTreeUtil.nextVisibleLeaf(element)

            else -> element
        } ?: return null

        val function = PsiTreeUtil.getParentOfType(leaf, PyFunction::class.java)
        if (function != null) {
            val containingClass = PsiTreeUtil.getParentOfType(function, PyClass::class.java, true)
            if (function.name == "__init__" && containingClass != null) {
                return containingClass
            }
            return if (containingClass == null) function else null
        }

        val pyClass = PsiTreeUtil.getParentOfType(leaf, PyClass::class.java)
        if (pyClass != null && PsiTreeUtil.getParentOfType(pyClass, PyClass::class.java, true) == null) {
            return pyClass
        }

        return null
    }
}
