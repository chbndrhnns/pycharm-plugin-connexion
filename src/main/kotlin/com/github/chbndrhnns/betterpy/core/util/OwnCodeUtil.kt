package com.github.chbndrhnns.betterpy.core.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

fun PsiElement.isOwnCode(): Boolean {
    val file = this.containingFile ?: return false
    return file.isOwnCode()
}

fun PsiFile.isOwnCode(): Boolean {
    val virtualFile = this.virtualFile ?: return false
    return isOwnCode(this.project, virtualFile)
}

fun isOwnCode(project: Project, file: VirtualFile): Boolean {
    val index = ProjectFileIndex.getInstance(project)
    return index.isInContent(file)
}
