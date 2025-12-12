package com.github.chbndrhnns.intellijplatformplugincopy.searcheverywhere

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
        val rawParts = StringUtil.split(pattern, "::")
        val parts = normalizePytestNodeIdParts(rawParts)
        if (parts.isEmpty()) return null

        // Extract file path and elements (class, function)
        val filePath = parts[0]
        val fileName = filePath.substringAfterLast('/')
        val scope = GlobalSearchScope.projectScope(project)

        // Find the Python file
        val psiManager = PsiManager.getInstance(project)
        val pyFiles = FilenameIndex.getVirtualFilesByName(fileName, scope)
            .mapNotNull { psiManager.findFile(it) }
            .filterIsInstance<PyFile>()

        for (pyFile in pyFiles) {
            // Check if this is the correct file by path
            val virtualFile = pyFile.virtualFile
            if (virtualFile != null && !virtualFile.path.endsWith(filePath)) {
                continue
            }

            // Navigate through the parts to find the target element
            var currentElement: PsiElement = pyFile

            for (i in 1 until parts.size) {
                val partName = stripParametrization(parts[i])

                currentElement = when (currentElement) {
                    is PyFile -> {
                        // Look for top-level class or function
                        currentElement.topLevelClasses.find { it.name == partName }
                            ?: currentElement.topLevelFunctions.find { it.name == partName } ?: return null
                    }

                    is PyClass -> {
                        // Look for nested class or method within class
                        currentElement.nestedClasses.find { it.name == partName } ?: currentElement.findMethodByName(
                            partName,
                            false,
                            null
                        ) ?: return null
                    }

                    else -> return null
                }
            }

            return currentElement
        }

        return null
    }

    private fun normalizePytestNodeIdParts(rawParts: List<String>): List<String> {
        if (rawParts.isEmpty()) return emptyList()
        if (rawParts.size == 1) return rawParts

        val normalized = ArrayList<String>(rawParts.size + 2)
        normalized.add(rawParts.first())

        for (i in 1 until rawParts.size) {
            normalized.addAll(splitContainerSegment(rawParts[i]))
        }

        return normalized
    }

    /**
     * Pytest node ids are canonically separated by `::`.
     *
     * When `pytest-sugar` is active, the console may render the post-file container chain as a single
     * dot-separated segment (e.g. `TestGetAll.test_returns_all`), while still keeping `::` between the
     * file part and the container part.
     */
    private fun splitContainerSegment(rawSegment: String): List<String> {
        val segmentWithoutParams = stripParametrization(rawSegment)
        return segmentWithoutParams
            .split('.')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun stripParametrization(name: String): String {
        val bracketIndex = name.indexOf('[')
        if (bracketIndex > 0 && name.endsWith("]")) {
            return name.substring(0, bracketIndex)
        }
        return name
    }
}
