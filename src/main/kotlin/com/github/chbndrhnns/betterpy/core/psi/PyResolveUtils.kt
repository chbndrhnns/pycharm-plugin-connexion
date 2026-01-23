package com.github.chbndrhnns.betterpy.core.psi

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext

object PyResolveUtils {
    fun resolveDottedName(name: String, context: PsiElement, resolveImported: Boolean = true): List<PsiElement> {
        val project = context.project
        val parts = name.split(".")
        if (parts.isEmpty()) return emptyList()

        val firstPart = parts[0]
        var current = findTopLevelModules(project, firstPart).toMutableList()

        // Check builtins if not found
        if (current.isEmpty()) {
            val builtinsList = findTopLevelModules(project, "builtins")
            for (builtins in builtinsList) {
                current.addAll(findMembers(builtins, firstPart, resolveImported))
            }
        }

        if (current.isEmpty()) return emptyList()

        var results = current.toList()

        for (i in 1 until parts.size) {
            val part = parts[i]
            val nextResults = mutableListOf<PsiElement>()
            for (res in results) {
                nextResults.addAll(findMembers(res, part, resolveImported))
            }
            if (nextResults.isEmpty()) return emptyList()
            results = nextResults.distinct()
        }
        return results
    }

    private fun findTopLevelModules(project: Project, name: String): List<PsiElement> {
        val scope = GlobalSearchScope.allScope(project)
        val psiManager = PsiManager.getInstance(project)
        val includeSourceRootPrefix = PluginSettingsState.instance().state.enableRestoreSourceRootPrefix
        val fileIndex = ProjectFileIndex.getInstance(project)
        val rootManager = ProjectRootManager.getInstance(project)
        val results = mutableListOf<PsiElement>()

        rootManager.contentSourceRoots.forEach { sourceRoot ->
            val contentRoot = fileIndex.getContentRootForFile(sourceRoot) ?: return@forEach
            if (!isAllowedTopLevelName(name, sourceRoot, fileIndex, includeSourceRootPrefix)) return@forEach

            val prefixPath = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '.')
            if (prefixPath.isNullOrEmpty()) return@forEach

            val firstComponent = prefixPath.substringBefore('.')
            if (firstComponent == name) {
                psiManager.findDirectory(sourceRoot)?.let { results.add(it) }
            }
        }

        // Try name.py
        val virtualFiles = FilenameIndex.getVirtualFilesByName("$name.py", scope)
        for (vf in virtualFiles) {
            if (!isAllowedTopLevelName(name, vf, fileIndex, includeSourceRootPrefix)) continue

            // If it's in the SDK or library, we allow it if it's a top-level module there
            if (fileIndex.isInLibraryClasses(vf) || !fileIndex.isInContent(vf)) {
                val file = psiManager.findFile(vf)
                if (file is PyFile) results.add(file)
            }

            // Ensure the file is directly under a source root or content root
            val sourceRoot = fileIndex.getSourceRootForFile(vf)
            val contentRoot = fileIndex.getContentRootForFile(vf)
            if (sourceRoot == vf.parent || contentRoot == vf.parent) {
                val file = psiManager.findFile(vf)
                if (file is PyFile) results.add(file)
            }
        }

        // Try package (directory with __init__.py)
        val initVFiles = FilenameIndex.getVirtualFilesByName("__init__.py", scope)
        for (vf in initVFiles) {
            if (!isAllowedTopLevelName(name, vf, fileIndex, includeSourceRootPrefix)) continue

            if (vf.parent?.name == name) {
                // If it's in the SDK or library, we allow it
                if (fileIndex.isInLibraryClasses(vf) || !fileIndex.isInContent(vf)) {
                    val psiFile = psiManager.findFile(vf) ?: continue
                    psiFile.parent?.let { results.add(it) }
                }

                // Ensure the package directory is directly under a source root or content root
                val pkgDir = vf.parent ?: continue
                val sourceRoot = fileIndex.getSourceRootForFile(pkgDir)
                val contentRoot = fileIndex.getContentRootForFile(pkgDir)
                if (sourceRoot == pkgDir.parent || contentRoot == pkgDir.parent) {
                    val psiFile = psiManager.findFile(vf) ?: continue
                    psiFile.parent?.let { results.add(it) }
                }
            }
        }
        return results.distinct()
    }

    private fun isAllowedTopLevelName(
        name: String,
        virtualFile: VirtualFile,
        fileIndex: ProjectFileIndex,
        includeSourceRootPrefix: Boolean
    ): Boolean {
        val sourceRoot = fileIndex.getSourceRootForFile(virtualFile) ?: return true
        val contentRoot = fileIndex.getContentRootForFile(virtualFile) ?: return true
        val prefixPath = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '.') ?: return true
        if (prefixPath.isEmpty()) return true
        val firstComponent = prefixPath.substringBefore('.')

        return if (includeSourceRootPrefix) {
            name == firstComponent
        } else {
            name != firstComponent
        }
    }

    private fun findMembers(element: PsiElement, name: String, resolveImported: Boolean = true): List<PsiElement> {
        val results = mutableListOf<PsiElement>()
        if (element is PsiDirectory) {
            val file = element.findFile("$name.py")
            if (file != null) results.add(file)

            val subdir = element.findSubdirectory(name)
            if (subdir != null && subdir.findFile("__init__.py") != null) results.add(subdir)

            val init = element.findFile("__init__.py")
            if (init is PyFile) {
                results.addAll(findMembers(init, name, resolveImported))
            }
        }
        if (element is PyFile) {
            element.findTopLevelClass(name)?.let { results.add(it) }
            element.findTopLevelFunction(name)?.let { results.add(it) }
            element.findTopLevelAttribute(name)?.let { results.add(it) }

            if (resolveImported) {
                // Check from imports
                val fromImports = PsiTreeUtil.findChildrenOfType(element, PyFromImportStatement::class.java)
                for (stmt in fromImports) {
                    for (el in stmt.importElements) {
                        val visibleName = el.asName ?: el.importedQName?.lastComponent
                        if (visibleName == name) {
                            val resolveResults = el.multiResolve()
                            for (res in RatedResolveResult.sorted(resolveResults)) {
                                res.element?.let { results.add(it) }
                            }
                        }
                    }
                }

                // Check regular imports
                val imports = PsiTreeUtil.findChildrenOfType(element, PyImportStatement::class.java)
                for (stmt in imports) {
                    for (el in stmt.importElements) {
                        val visibleName = el.asName ?: el.importedQName?.firstComponent
                        if (visibleName == name) {
                            val resolveResults = el.multiResolve()
                            for (res in RatedResolveResult.sorted(resolveResults)) {
                                res.element?.let { results.add(it) }
                            }
                        }
                    }
                }
            }
        }
        if (element is PyClass) {
            element.findNestedClass(name, true)?.let { results.add(it) }
            element.findMethodByName(name, true, null)?.let { results.add(it) }
            element.findInstanceAttribute(name, true)?.let { results.add(it) }
            element.findClassAttribute(name, true, null)?.let { results.add(it) }
        }
        if (element is PyTypedElement && (resolveImported || element !is PyFile)) {
            val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
            val type = context.getType(element)
            if (type != null) {
                val resolveContext = PyResolveContext.defaultContext(context)
                val members =
                    type.resolveMember(name, null, com.jetbrains.python.psi.AccessDirection.READ, resolveContext)
                if (!members.isNullOrEmpty()) {
                    for (member in members) {
                        member.element?.let { results.add(it) }
                    }
                }
            }
        }
        return results.distinct()
    }

    fun getVariants(element: PsiElement?): Array<Any> {
        val result = mutableListOf<Any>()
        if (element == null) return emptyArray()

        if (element is PsiDirectory) {
            element.files.forEach { file ->
                if (file is PyFile && file.name != "__init__.py") {
                    val name = file.name.removeSuffix(".py")
                    result.add(LookupElementBuilder.create(file, name).withIcon(file.getIcon(0)))
                }
            }
            element.subdirectories.forEach { subdir ->
                if (subdir.findFile("__init__.py") != null) {
                    result.add(subdir)
                }
            }
        } else if (element is PyFile) {
            result.addAll(element.topLevelClasses)
            result.addAll(element.topLevelFunctions)
            result.addAll(element.topLevelAttributes)
        } else if (element is PyClass) {
            result.addAll(element.methods)
            result.addAll(element.nestedClasses)
            result.addAll(element.instanceAttributes)
            result.addAll(element.classAttributes)
        }
        return result.toTypedArray()
    }
}
