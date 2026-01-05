package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.openapi.roots.ProjectFileIndex
import com.jetbrains.python.psi.PyFunction

object PytestLocationUrlFactory {
    fun fromPyFunction(function: PyFunction): String? {
        val qName = function.qualifiedName ?: return null
        val virtualFile = function.containingFile.virtualFile ?: return null
        val project = function.project

        // SMTestProxy.locationUrl uses the content root in angle brackets
        // We use the content root for the file, falling back to project base path
        val fileIndex = ProjectFileIndex.getInstance(project)
        val contentRoot = fileIndex.getContentRootForFile(virtualFile)
        val contentRootPath = contentRoot?.path ?: project.basePath ?: return null

        return "python<$contentRootPath>://$qName"
    }
}
