package com.github.chbndrhnns.betterpy.features.refactoring.move

import com.intellij.psi.PsiElement

class PyMoveModel(
    val elements: List<PsiElement>,
    val targetModulePath: String,
    val searchReferences: Boolean = true
) {
    fun isValidRefactoring(): Boolean {
        return elements.isNotEmpty() && targetModulePath.isNotBlank()
    }

    fun toDescriptor(): PyMoveOperationDescriptor {
        return PyMoveOperationDescriptor.Declarations(
            elements = elements,
            targetModulePath = targetModulePath,
            searchReferences = searchReferences
        )
    }
}
