package com.github.chbndrhnns.intellijplatformplugincopy.features.imports

import com.github.chbndrhnns.intellijplatformplugincopy.core.services.PythonStdlibService
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder
import com.jetbrains.python.codeInsight.imports.PyImportCandidateProvider
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.PythonSdkUtil
import java.io.File

/**
 * Filters auto-import suggestions to hide transient dependencies.
 * Only shows imports for packages that are direct dependencies in pyproject.toml.
 */
class HideTransientImportProvider : PyImportCandidateProvider {
    companion object {
        private val MODULE_TO_PACKAGE_KEY = Key.create<CachedValue<Map<String, String>>>("MODULE_TO_PACKAGE_MAPPING")
    }
    override fun addImportCandidates(reference: PsiReference, name: String, quickFix: AutoImportQuickFix) {
        if (!PluginSettingsState.instance().state.enableHideTransientImports) {
            return
        }
        val element = reference.element
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
        val directDependencies = getDirectDependencies(module) ?: return
        val project = element.project
        val stdlibService = PythonStdlibService.getInstance(project)
        filterTransientCandidatesReflectively(quickFix, directDependencies, stdlibService, module)
    }

    /**
     * Gets the cached module-to-package mapping, rebuilding it only when the SDK or packages change.
     */
    private fun getCachedModuleToPackageMapping(module: Module): Map<String, String> {
        return CachedValuesManager.getManager(module.project).getCachedValue(
            module,
            MODULE_TO_PACKAGE_KEY,
            {
                val mapping = buildModuleToPackageMapping(module)

                // Invalidate cache when project roots change (e.g., SDK or packages installed/removed)
                CachedValueProvider.Result.create(
                    mapping,
                    ProjectRootManager.getInstance(module.project)
                )
            },
            false
        )
    }

    /**
     * Extracts top-level module names from a RECORD file.
     * Parses CSV-like entries, strips lines with .dist-info suffix before the first slash,
     * and collects all unique elements before the first slash.
     */
    private fun extractModulesFromRecord(recordFile: File): Set<String> {
        val modules = mutableSetOf<String>()

        try {
            recordFile.readLines().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) return@forEach

                // Extract the file path (first CSV field before the first comma)
                val filePath = trimmedLine.substringBefore(',')
                if (filePath.isEmpty()) return@forEach

                // Skip lines with .dist-info suffix before the first slash
                val firstSlashIndex = filePath.indexOf('/')
                if (firstSlashIndex > 0) {
                    val beforeSlash = filePath.substring(0, firstSlashIndex)
                    if (beforeSlash.endsWith(".dist-info") || beforeSlash.endsWith(".egg-info")) {
                        return@forEach
                    }

                    // Collect the top-level directory name
                    modules.add(beforeSlash)
                }
            }
        } catch (e: Exception) {
            Logger.getInstance(HideTransientImportProvider::class.java)
                .debug("Failed to parse RECORD file: ${recordFile.name}", e)
        }

        return modules
    }

    /**
     * Builds a mapping from top-level module names to package names
     * by reading top_level.txt from .dist-info and .egg-info directories.
     * Falls back to RECORD file if top_level.txt doesn't exist.
     */
    private fun buildModuleToPackageMapping(module: Module): Map<String, String> {
        val sdk = PythonSdkUtil.findPythonSdk(module) ?: return emptyMap()
        val sitePackagesDirs = sdk.rootProvider.getFiles(com.intellij.openapi.roots.OrderRootType.CLASSES)

        val mapping = mutableMapOf<String, String>()

        for (sitePackagesVFile in sitePackagesDirs) {
            val sitePackagesDir = File(sitePackagesVFile.path)
            if (!sitePackagesDir.exists() || !sitePackagesDir.isDirectory) continue

            // Find all .dist-info and .egg-info directories
            val metadataDirs = sitePackagesDir.listFiles { file ->
                file.isDirectory && (file.name.endsWith(".dist-info") || file.name.endsWith(".egg-info"))
            } ?: continue

            for (metadataDir in metadataDirs) {
                try {
                    // Extract package name from directory name (e.g., "Pillow-9.0.0.dist-info" -> "Pillow")
                    val dirName = metadataDir.name
                    val packageName = when {
                        dirName.endsWith(".dist-info") -> dirName.removeSuffix(".dist-info").substringBeforeLast('-')
                        dirName.endsWith(".egg-info") -> dirName.removeSuffix(".egg-info").substringBeforeLast('-')
                        else -> continue
                    }
                    val normalizedPackageName = PyPackageName.normalizePackageName(packageName)

                    // Read top_level.txt
                    val topLevelFile = File(metadataDir, "top_level.txt")
                    if (topLevelFile.exists() && topLevelFile.isFile) {
                        val topLevelModules = topLevelFile.readLines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }

                        for (moduleName in topLevelModules) {
                            val normalizedModule = moduleName.lowercase().replace("-", "_")
                            mapping[normalizedModule] = normalizedPackageName
                        }
                    } else {
                        // Fallback 1: Try to extract modules from RECORD file
                        val recordFile = File(metadataDir, "RECORD")
                        if (recordFile.exists() && recordFile.isFile) {
                            val modulesFromRecord = extractModulesFromRecord(recordFile)
                            for (moduleName in modulesFromRecord) {
                                val normalizedModule = moduleName.lowercase().replace("-", "_")
                                mapping[normalizedModule] = normalizedPackageName
                            }
                        } else {
                            // Fallback 2: assume module name matches package name
                            val normalizedModule = normalizedPackageName.replace("-", "_")
                            mapping[normalizedModule] = normalizedPackageName
                        }
                    }
                } catch (e: Exception) {
                    // Skip this package on error
                    Logger.getInstance(HideTransientImportProvider::class.java)
                        .debug("Failed to process metadata directory: ${metadataDir.name}", e)
                }
            }
        }

        return mapping
    }

    /**
     * Resolves the package name for a given module using multiple strategies:
     * 1. Installed package metadata (module-to-package mapping)
     * 2. Hardcoded mappings (PyPsiPackageUtil)
     * 3. Simple normalization fallback
     */
    private fun resolvePackageName(
        topLevelModule: String,
        moduleToPackage: Map<String, String>
    ): String {
        val normalizedModule = topLevelModule.lowercase().replace("-", "_")

        // Strategy 1: Use metadata from installed packages
        moduleToPackage[normalizedModule]?.let { return it }

        // Strategy 2: Use PyCharm's hardcoded mappings
        val fromHardcoded = PyPsiPackageUtil.moduleToPackageName(topLevelModule)
        val normalizedHardcoded = PyPackageName.normalizePackageName(fromHardcoded)

        // Strategy 3: Fallback to normalized module name
        return normalizedHardcoded
    }

    /**
     * Filters candidates to remove those from transient dependencies.
     * Uses reflection to access the internal mutable list.
     * Never filters stdlib modules.
     */
    private fun filterTransientCandidatesReflectively(
        quickFix: AutoImportQuickFix,
        directDependencies: Set<String>,
        stdlibService: PythonStdlibService,
        module: Module
    ) {
        try {
            val candidatesField = AutoImportQuickFix::class.java.getDeclaredField("myImports")
            candidatesField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val candidates = candidatesField.get(quickFix) as? MutableList<ImportCandidateHolder> ?: return

            // Get cached module-to-package mapping from installed packages
            val moduleToPackage = getCachedModuleToPackageMapping(module)

            // Filter using the public API of ImportCandidateHolder
            candidates.removeIf { candidate ->
                // Never filter local project modules
                val importable = candidate.importable
                if (isLocal(importable, module)) return@removeIf false

                val path = candidate.path ?: return@removeIf false // Keep if no path (built-ins, etc.)
                val topLevelModule = path.firstComponent ?: return@removeIf false

                // Never filter stdlib modules
                if (stdlibService.isStdlibModule(topLevelModule, null)) return@removeIf false

                // Try to resolve package name using multiple strategies
                val packageName = resolvePackageName(topLevelModule, moduleToPackage)

                // Filter out if not in direct dependencies
                !directDependencies.contains(packageName)
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

    private fun isLocal(importable: PsiElement?, module: Module): Boolean {
        if (importable == null) return false
        val candidateModule = ModuleUtilCore.findModuleForPsiElement(importable)
        return candidateModule != null && candidateModule == module
    }

    /**
     * Retrieves the set of direct dependencies from pyproject.toml.
     * Returns null if pyproject.toml is not found or cannot be parsed.
     */
    private fun getDirectDependencies(module: Module): Set<String>? {
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
