package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

object PytestNodeIdGenerator {
    fun getId(proxy: SMTestProxy, project: Project): String? {
        // 1. Try to get PSI element from location
        val location = proxy.getLocation(project, GlobalSearchScope.allScope(project))
        val psiElement = location?.psiElement

        if (psiElement != null) {
            return generateFromPsi(psiElement, project)
        }

        // 3. Fallback to proxy names if PSI is missing
        return generateFromProxyHierarchy(proxy)
    }

    private fun generateFromPsi(element: PsiElement, project: Project): String? {
        val containingFile = element.containingFile ?: return null
        val virtualFile = containingFile.virtualFile ?: return null

        val fileIndex = com.intellij.openapi.roots.ProjectRootManager.getInstance(project).fileIndex
        val contentRoot = fileIndex.getContentRootForFile(virtualFile)

        val relativePath = if (contentRoot != null) {
            VfsUtil.getRelativePath(virtualFile, contentRoot) ?: virtualFile.path
        } else {
            val basePath = project.basePath
            val baseDir = if (basePath != null) {
                virtualFile.fileSystem.findFileByPath(basePath)
            } else null

            if (baseDir != null) {
                VfsUtil.getRelativePath(virtualFile, baseDir) ?: virtualFile.path
            } else {
                virtualFile.path
            }
        }

        val hierarchy = mutableListOf<String>()
        var current: PsiElement? = element

        while (current != null && current !is PyFile) {
            if (current is PyClass) {
                hierarchy.add(0, current.name ?: "")
            } else if (current is PyFunction) {
                hierarchy.add(0, current.name ?: "")
            }
            current = current.parent
        }

        if (hierarchy.isEmpty()) {
            // If we pointed to a file, just return path
            return relativePath
        }

        return relativePath + "::" + hierarchy.joinToString("::")
    }

    private fun generateFromProxyHierarchy(proxy: SMTestProxy): String? {
        val parts = mutableListOf<String>()
        var current: SMTestProxy? = proxy

        while (current != null && current.parent != null) { // Stop before root
            parts.add(0, current.name)
            current = current.parent
        }

        return parts.joinToString("::")
    }
}
