package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ProjectFileIndex
import com.jetbrains.python.psi.PyFunction

object PytestLocationUrlFactory {
    private val LOG = Logger.getInstance(PytestLocationUrlFactory::class.java)
    
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
        LOG.debug("PytestLocationUrlFactory.fromPyFunction: generating URLs for function")

        val qName = function.qualifiedName
        if (qName == null) {
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: qualified name is null, returning empty list")
            return emptyList()
        }
        LOG.debug("PytestLocationUrlFactory.fromPyFunction: qualified name = '$qName'")

        val virtualFile = function.containingFile.virtualFile
        if (virtualFile == null) {
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: virtual file is null, returning empty list")
            return emptyList()
        }
        LOG.debug("PytestLocationUrlFactory.fromPyFunction: virtual file path = '${virtualFile.path}'")

        val project = function.project

        val fileIndex = ProjectFileIndex.getInstance(project)
        val sourceRoot = fileIndex.getSourceRootForFile(virtualFile)
        val contentRoot = fileIndex.getContentRootForFile(virtualFile)
        val projectBasePath = project.basePath

        LOG.debug("PytestLocationUrlFactory.fromPyFunction: source root = '${sourceRoot?.path}', content root = '${contentRoot?.path}', project base path = '$projectBasePath'")

        val urls = mutableListOf<String>()

        // Add source root based URL (e.g., tests directory if it's a source root)
        if (sourceRoot != null) {
            val url = "python<${sourceRoot.path}>://$qName"
            urls.add(url)
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: added source root URL: '$url'")
        }

        // Add content root based URL if different from source root
        if (contentRoot != null && contentRoot.path != sourceRoot?.path) {
            val url = "python<${contentRoot.path}>://$qName"
            urls.add(url)
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: added content root URL: '$url'")
        }

        // Add project root based URL if different from both source and content roots
        if (projectBasePath != null &&
            projectBasePath != sourceRoot?.path &&
            projectBasePath != contentRoot?.path
        ) {
            val url = "python<$projectBasePath>://$qName"
            urls.add(url)
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: added project base path URL: '$url'")
        }

        LOG.debug("PytestLocationUrlFactory.fromPyFunction: returning ${urls.size} URL(s): $urls")
        return urls
    }
}
