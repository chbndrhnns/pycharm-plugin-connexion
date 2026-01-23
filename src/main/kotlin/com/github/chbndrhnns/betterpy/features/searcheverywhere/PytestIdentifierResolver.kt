package com.github.chbndrhnns.betterpy.features.searcheverywhere

import com.github.chbndrhnns.betterpy.core.pytest.PytestNodeIdUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile

class PytestIdentifierResolver(private val project: Project) {

    fun resolve(pattern: String): PsiElement? {
        return resolveAll(pattern).firstOrNull()
    }

    fun resolveAll(pattern: String): List<PsiElement> {
        val rawParts = StringUtil.split(pattern, "::")
        val parts = PytestNodeIdUtil.normalizePytestNodeIdParts(rawParts)
        if (parts.isEmpty()) return emptyList()

        // Extract file path and elements (class, function)
        val filePath = parts[0]
        val fileName =
            if (filePath.endsWith(".py")) filePath.substringAfterLast('/') else "${filePath.substringAfterLast('/')}.py"
        val scope = GlobalSearchScope.projectScope(project)

        // Find the Python file
        val psiManager = PsiManager.getInstance(project)

        // Try exact filename first
        var pyFiles = FilenameIndex.getVirtualFilesByName(fileName, scope)
            .mapNotNull { psiManager.findFile(it) }
            .filterIsInstance<PyFile>()

        // If no exact match, and it's short, maybe we shouldn't search all files.
        // But if it contains ::, the user is being specific.
        if (pyFiles.isEmpty() && !filePath.endsWith(".py")) {
            // Try searching for filenames containing filePath
            pyFiles = FilenameIndex.getAllFilesByExt(project, "py", scope)
                .filter { it.name.contains(filePath, ignoreCase = true) }
                .mapNotNull { psiManager.findFile(it) }
                .filterIsInstance<PyFile>()
        }

        val results = mutableListOf<PsiElement>()

        for (pyFile in pyFiles) {
            // Check if this is the correct file by path
            val virtualFile = pyFile.virtualFile
            if (virtualFile != null) {
                val path = virtualFile.path
                if (filePath.contains("/")) {
                    // If filePath contains separators, it's likely a more complete path
                    if (!path.endsWith(filePath) && !path.endsWith("$filePath.py")) {
                        continue
                    }
                }
            }

            // Navigate through the parts to find the target element
            var currentElements: List<PsiElement> = listOf(pyFile)

            for (i in 1 until parts.size) {
                val partName = PytestNodeIdUtil.stripParametrization(parts[i])
                val nextElements = mutableListOf<PsiElement>()

                for (element in currentElements) {
                    nextElements.addAll(findMatchingElements(element, partName))
                }
                currentElements = nextElements
                if (currentElements.isEmpty()) break
            }
            results.addAll(currentElements)
        }

        return results.distinct()
    }

    private fun findMatchingElements(element: PsiElement, partName: String): List<PsiElement> {
        val matches = mutableListOf<PsiElement>()

        fun collectMatches(current: PsiElement, recursive: Boolean) {
            when (current) {
                is PyFile -> {
                    current.topLevelClasses.filter { it.name?.contains(partName, ignoreCase = true) == true }
                        .forEach { matches.add(it) }
                    current.topLevelFunctions.filter { it.name?.contains(partName, ignoreCase = true) == true }
                        .forEach { matches.add(it) }

                    if (recursive) {
                        current.topLevelClasses.forEach { collectMatches(it, true) }
                    }
                }

                is PyClass -> {
                    current.nestedClasses.filter { it.name?.contains(partName, ignoreCase = true) == true }
                        .forEach { matches.add(it) }
                    current.methods.filter { it.name?.contains(partName, ignoreCase = true) == true }
                        .forEach { matches.add(it) }

                    if (recursive) {
                        current.nestedClasses.forEach { collectMatches(it, true) }
                    }
                }
            }
        }

        // First try non-recursive (direct children)
        collectMatches(element, false)

        // If no direct matches, try recursive
        if (matches.isEmpty()) {
            collectMatches(element, true)
        }

        return matches
    }

}
