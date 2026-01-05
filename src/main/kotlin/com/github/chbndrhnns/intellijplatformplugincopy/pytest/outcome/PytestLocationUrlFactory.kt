package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.openapi.roots.ProjectFileIndex
import com.jetbrains.python.psi.PyFunction

object PytestLocationUrlFactory {
    /**
     * Generate possible location URLs for a test function.
     * 
     * Pytest can use different paths depending on project structure:
     * - Project root: python</path/to/project>://module.test_func
     * - Test source root: python</path/to/project/tests>://module.test_func
     * 
     * We return both possibilities to handle all cases.
     */
    fun fromPyFunction(function: PyFunction): List<String> {
        val qName = function.qualifiedName ?: return emptyList()

        val virtualFile = function.containingFile.virtualFile ?: return emptyList()

        val project = function.project

        val fileIndex = ProjectFileIndex.getInstance(project)
        val contentRoot = fileIndex.getContentRootForFile(virtualFile)
        val projectBasePath = project.basePath

        val urls = mutableListOf<String>()

        // Add content root based URL (e.g., tests directory if it's a source root)
        if (contentRoot != null) {
            urls.add("python<${contentRoot.path}>://$qName")
        }

        // Add project root based URL if different from content root
        if (projectBasePath != null && contentRoot?.path != projectBasePath) {
            urls.add("python<$projectBasePath>://$qName")
        }

        return urls
    }
}
