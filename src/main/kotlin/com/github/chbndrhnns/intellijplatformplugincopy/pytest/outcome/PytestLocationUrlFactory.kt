package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.jetbrains.python.psi.PyFunction

object PytestLocationUrlFactory {
    fun fromPyFunction(function: PyFunction): String? {
        val qName = function.qualifiedName ?: return null
        val virtualFile = function.containingFile.virtualFile ?: return null
        val project: Project = function.project

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val root = fileIndex.getSourceRootForFile(virtualFile) ?: fileIndex.getContentRootForFile(virtualFile)
        return if (root != null) {
            "python<${root.path}>://$qName"
        } else {
            "python:$qName"
        }
    }
}
