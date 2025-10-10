package com.github.chbndrhnns.connexion.core.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

fun PsiFile.isOwnCode(): Boolean {
    val virtualFile = this.virtualFile ?: return false
    return isOwnCode(this.project, virtualFile)
}

fun isOwnCode(project: Project, file: VirtualFile): Boolean {
    val index = ProjectFileIndex.getInstance(project)
    return index.isInContent(file)
}
