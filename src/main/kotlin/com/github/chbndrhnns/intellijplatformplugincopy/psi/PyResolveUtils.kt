package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
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
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportStatement

object PyResolveUtils {
    fun resolveDottedName(name: String, context: PsiElement, resolveImported: Boolean = true): PsiElement? {
        val project = context.project
        val parts = name.split(".")
        if (parts.isEmpty()) return null

        val firstPart = parts[0]
        var current: PsiElement? = findTopLevelModule(project, firstPart)

        // Check builtins if not found
        if (current == null) {
            val builtins = findTopLevelModule(project, "builtins")
            if (builtins != null) {
                val member = findMember(builtins, firstPart, resolveImported)
                if (member != null) {
                    current = member
                    // firstPart consumed as member of builtins
                }
            }
        }

        var result = current ?: return null

        for (i in 1 until parts.size) {
            val part = parts[i]
            val next = findMember(result, part, resolveImported) ?: return null
            result = next
        }
        return result
    }

    private fun findTopLevelModule(project: Project, name: String): PsiElement? {
        val scope = GlobalSearchScope.allScope(project)
        val psiManager = PsiManager.getInstance(project)
        val includeSourceRootPrefix = PluginSettingsState.instance().state.enableRestoreSourceRootPrefix
        val fileIndex = ProjectFileIndex.getInstance(project)
        val rootManager = ProjectRootManager.getInstance(project)

        rootManager.contentSourceRoots.forEach { sourceRoot ->
            val contentRoot = fileIndex.getContentRootForFile(sourceRoot) ?: return@forEach
            if (!isAllowedTopLevelName(name, sourceRoot, fileIndex, includeSourceRootPrefix)) return@forEach

            val prefixPath = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '.')
            if (prefixPath.isNullOrEmpty()) return@forEach

            val firstComponent = prefixPath.substringBefore('.')
            if (firstComponent == name) {
                psiManager.findDirectory(sourceRoot)?.let { return it }
            }
        }

        // Try name.py
        val virtualFiles = FilenameIndex.getVirtualFilesByName("$name.py", scope)
        for (vf in virtualFiles) {
            if (!isAllowedTopLevelName(name, vf, fileIndex, includeSourceRootPrefix)) continue
            val file = psiManager.findFile(vf)
            if (file is PyFile) return file
        }

        // Try package (directory with __init__.py)
        val initVFiles = FilenameIndex.getVirtualFilesByName("__init__.py", scope)
        for (vf in initVFiles) {
            if (!isAllowedTopLevelName(name, vf, fileIndex, includeSourceRootPrefix)) continue
            if (vf.parent?.name == name) {
                val psiFile = psiManager.findFile(vf) ?: continue
                return psiFile.parent
            }
        }
        return null
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

    private fun findMember(element: PsiElement, name: String, resolveImported: Boolean = true): PsiElement? {
        if (element is PsiDirectory) {
            val file = element.findFile("$name.py")
            if (file != null) return file

            val subdir = element.findSubdirectory(name)
            if (subdir != null && subdir.findFile("__init__.py") != null) return subdir

            val init = element.findFile("__init__.py")
            if (init is PyFile) {
                return findMember(init, name, resolveImported)
            }
        }
        if (element is PyFile) {
            val declared = element.findTopLevelClass(name) ?: element.findTopLevelFunction(name)
            ?: element.findTopLevelAttribute(
                name
            )
            if (declared != null) return declared

            if (resolveImported) {
                // Check from imports
                val fromImports = PsiTreeUtil.findChildrenOfType(element, PyFromImportStatement::class.java)
                for (stmt in fromImports) {
                    for (el in stmt.importElements) {
                        val visibleName = el.asName ?: el.importedQName?.lastComponent
                        if (visibleName == name) {
                            return el.resolve()
                        }
                    }
                }

                // Check regular imports
                val imports = PsiTreeUtil.findChildrenOfType(element, PyImportStatement::class.java)
                for (stmt in imports) {
                    for (el in stmt.importElements) {
                        val visibleName = el.asName ?: el.importedQName?.firstComponent
                        if (visibleName == name) {
                            return el.resolve()
                        }
                    }
                }
            }

            return null
        }
        if (element is PyClass) {
            return element.findNestedClass(name, true) ?: element.findMethodByName(name, true, null)
            ?: element.findInstanceAttribute(name, true) ?: element.findClassAttribute(name, true, null)
        }
        return null
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
