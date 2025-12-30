package com.github.chbndrhnns.intellijplatformplugincopy.imports

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiReference
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder
import com.jetbrains.python.codeInsight.imports.PyImportCandidateProvider
import com.jetbrains.python.packaging.PyPackageName

/**
 * Filters auto-import suggestions to hide transient dependencies.
 * Only shows imports for packages that are direct dependencies in pyproject.toml.
 */
class HideTransientImportProvider : PyImportCandidateProvider {
    override fun addImportCandidates(reference: PsiReference, name: String, quickFix: AutoImportQuickFix) {
        if (!PluginSettingsState.instance().state.enableHideTransientImports) {
            return
        }
        val directDependencies = getDirectDependencies(reference) ?: return
        filterTransientCandidatesReflectively(quickFix, directDependencies)
    }

    /**
     * Filters candidates to remove those from transient dependencies.
     * Uses reflection to access the internal mutable list.
     */
    private fun filterTransientCandidatesReflectively(
        quickFix: AutoImportQuickFix,
        directDependencies: Set<String>
    ) {
        try {
            val candidatesField = AutoImportQuickFix::class.java.getDeclaredField("myImports")
            candidatesField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val candidates = candidatesField.get(quickFix) as? MutableList<ImportCandidateHolder> ?: return

            // Filter using the public API of ImportCandidateHolder
            candidates.removeIf { candidate ->
                val path = candidate.path ?: return@removeIf false // Keep if no path (built-ins, etc.)
                val topLevelModule = path.firstComponent ?: return@removeIf false
                val packageName = PyPsiPackageUtil.moduleToPackageName(topLevelModule)
                val normalizedPackageName = PyPackageName.normalizePackageName(packageName)
                !directDependencies.contains(normalizedPackageName)
            }
        } catch (e: NoSuchFieldException) {
            // Field name changed - log and fail gracefully
            Logger.getInstance(HideTransientImportProvider::class.java)
                .warn("Failed to filter import candidates: AutoImportQuickFix structure changed", e)
        } catch (e: Exception) {
            // Other reflection errors - fail silently
            Logger.getInstance(HideTransientImportProvider::class.java)
                .debug("Failed to filter import candidates", e)
        }
    }

    /**
     * Retrieves the set of direct dependencies from pyproject.toml.
     * Returns null if pyproject.toml is not found or cannot be parsed.
     */
    private fun getDirectDependencies(reference: PsiReference): Set<String>? {
        val element = reference.element
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
        val pyProjectFile = findPyProjectToml(module) ?: return null
        val dependencies = parseDependenciesFromToml(pyProjectFile)
        return dependencies.map { dep ->
            // Extract package name from dependency specification (e.g., "requests>=2.0.0" -> "requests")
            val packageName = dep.split(Regex("[><=!\\[;@ ]"))[0].trim()
            PyPackageName.normalizePackageName(packageName)
        }.toSet()
    }

    /**
     * Finds pyproject.toml in the module's content roots.
     */
    private fun findPyProjectToml(module: Module): VirtualFile? {
        val contentRoots = ModuleRootManager.getInstance(module).contentRoots
        for (root in contentRoots) {
            val pyProjectFile = root.findChild("pyproject.toml")
            if (pyProjectFile != null && pyProjectFile.exists()) {
                return pyProjectFile
            }
        }
        return null
    }

    /**
     * Parses dependencies from pyproject.toml file.
     * Supports both [project.dependencies] and [tool.poetry.dependencies] formats.
     */
    private fun parseDependenciesFromToml(file: VirtualFile): List<String> {
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        val dependencies = mutableListOf<String>()

        // Parse [project] dependencies (PEP 621)
        val projectDepsRegex = Regex("""(?s)\[project\].*?dependencies\s*=\s*\[(.*?)\]""")
        val projectMatch = projectDepsRegex.find(content)
        if (projectMatch != null) {
            val depsBlock = projectMatch.groupValues[1]
            val depRegex = Regex("""["']([^"']+)["']""")
            depRegex.findAll(depsBlock).forEach { match ->
                dependencies.add(match.groupValues[1])
            }
        }

        // Parse [tool.poetry.dependencies] (Poetry format)
        val poetryDepsRegex = Regex("""(?s)\[tool\.poetry\.dependencies\](.*?)(?=\[|$)""")
        val poetryMatch = poetryDepsRegex.find(content)
        if (poetryMatch != null) {
            val depsBlock = poetryMatch.groupValues[1]
            val depRegex = Regex("""^([a-zA-Z0-9_-]+)\s*=""", RegexOption.MULTILINE)
            depRegex.findAll(depsBlock).forEach { match ->
                val depName = match.groupValues[1]
                // Skip python version specification
                if (depName != "python") {
                    dependencies.add(depName)
                }
            }
        }

        return dependencies
    }
}
