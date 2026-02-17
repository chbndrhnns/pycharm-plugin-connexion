package com.github.chbndrhnns.betterpy.features.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

sealed class PyMoveOperationDescriptor {
    abstract fun run(project: Project)

    data class Declarations(
        val elements: List<PsiElement>,
        val targetModulePath: String,
        val searchReferences: Boolean
    ) : PyMoveOperationDescriptor() {
        override fun run(project: Project) {
            PyMoveDeclarationsProcessor(
                project = project,
                elements = elements,
                targetModulePath = targetModulePath,
                searchReferences = searchReferences
            ).run()
        }
    }
}
