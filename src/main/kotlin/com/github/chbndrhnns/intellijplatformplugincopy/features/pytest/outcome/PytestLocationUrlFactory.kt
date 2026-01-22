package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.outcome

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
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

        val functionName = function.name
        if (functionName == null) {
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: function name is null, returning empty list")
            return emptyList()
        }

        // Get the function's qualified name (for backward compatibility with content/project roots)
        val functionQName = function.qualifiedName
        if (functionQName == null) {
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: function qualified name is null, returning empty list")
            return emptyList()
        }
        LOG.debug("PytestLocationUrlFactory.fromPyFunction: function qualified name = '$functionQName'")

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
            val qName = buildQualifiedName(virtualFile, sourceRoot, function, isSourceRoot = true)
            val url = "python<${sourceRoot.path}>://$qName"
            urls.add(url)
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: added source root URL: '$url'")
        }

        // Add content root based URL if different from source root
        // Use the original qualified name for backward compatibility
        if (contentRoot != null && contentRoot.path != sourceRoot?.path) {
            val url = "python<${contentRoot.path}>://$functionQName"
            urls.add(url)
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: added content root URL: '$url'")
        }

        // Add project root based URL if different from both source and content roots
        // Use the original qualified name for backward compatibility
        if (projectBasePath != null &&
            projectBasePath != sourceRoot?.path &&
            projectBasePath != contentRoot?.path
        ) {
            val url = "python<$projectBasePath>://$functionQName"
            urls.add(url)
            LOG.debug("PytestLocationUrlFactory.fromPyFunction: added project base path URL: '$url'")
        }

        LOG.debug("PytestLocationUrlFactory.fromPyFunction: returning ${urls.size} URL(s): $urls")
        return urls
    }

    /**
     * Build a qualified name for a test function relative to a root directory.
     * For example, if the file is at root/tests/test_foo.py and root is the source root,
     * the qualified name for function test_bar would be "tests.test_foo.test_bar".
     * 
     * Special case: if the file is directly in a source root directory (e.g., tests/test_.py
     * where tests is marked as source root), we need to include the source root directory name
     * in the qualified name to match pytest's behavior.
     * 
     * @param isSourceRoot true if the root is a source root, false if it's a content/project root
     */
    private fun buildQualifiedName(
        file: VirtualFile,
        root: VirtualFile,
        function: PyFunction,
        isSourceRoot: Boolean
    ): String {
        val functionName = function.name ?: ""
        val relativePath =
            VfsUtilCore.getRelativePath(file, root) ?: // File is not under this root, just use the function name
            return functionName

        // Convert file path to Python module path
        // e.g., "unit/test_foo.py" -> "unit.test_foo"
        // e.g., "test_foo.py" -> "test_foo"
        val modulePath = relativePath
            .removeSuffix(".py")
            .replace('/', '.')

        // Special handling for source roots:
        // Pytest always includes the source root directory name in the qualified name
        // For example, if "tests" is a source root:
        //   - tests/test_.py -> tests.test_
        //   - tests/unit/test_.py -> tests.unit.test_
        // For content/project roots, we never prepend the root name
        val finalModulePath = if (isSourceRoot) {
            val rootName = root.name
            "$rootName.$modulePath"
        } else {
            modulePath
        }

        // Combine module path with function name
        // Handle nested classes: Class1.Class2.method
        val classChain = mutableListOf<String>()
        var currentElement: com.intellij.psi.PsiElement? = function.parent
        while (currentElement != null && currentElement !is com.jetbrains.python.psi.PyFile) {
            if (currentElement is com.jetbrains.python.psi.PyClass) {
                val name = currentElement.name
                if (name != null) {
                    classChain.add(0, name)
                }
            }
            currentElement = currentElement.parent
        }

        return if (classChain.isNotEmpty()) {
            val fullClassName = classChain.joinToString(".")
            "$finalModulePath.$fullClassName.$functionName"
        } else {
            "$finalModulePath.$functionName"
        }
    }
}
