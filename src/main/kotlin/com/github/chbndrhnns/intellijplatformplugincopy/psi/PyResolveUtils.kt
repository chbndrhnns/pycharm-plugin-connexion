package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile

object PyResolveUtils {
    fun resolveDottedName(name: String, context: PsiElement): PsiElement? {
        val project = context.project
        val parts = name.split(".")
        if (parts.isEmpty()) return null

        val firstPart = parts[0]
        var current: PsiElement? = findTopLevelModule(project, firstPart)

        // Check builtins if not found
        if (current == null) {
            val builtins = findTopLevelModule(project, "builtins")
            if (builtins != null) {
                val member = findMember(builtins, firstPart)
                if (member != null) {
                    current = member
                    // firstPart consumed as member of builtins
                }
            }
        }

        if (current == null) return null

        for (i in 1 until parts.size) {
            val part = parts[i]
            val next = findMember(current!!, part)
            if (next == null) return null
            current = next
        }
        return current
    }

    private fun findTopLevelModule(project: Project, name: String): PsiElement? {
        val scope = GlobalSearchScope.allScope(project)

        // Try name.py
        val files = FilenameIndex.getFilesByName(project, "$name.py", scope)
        val pyFile = files.firstOrNull { it is PyFile }
        if (pyFile != null) return pyFile

        // Try package (directory with __init__.py)
        val inits = FilenameIndex.getFilesByName(project, "__init__.py", scope)
        for (init in inits) {
            if (init.parent?.name == name) {
                return init.parent
            }
        }
        return null
    }

    private fun findMember(element: PsiElement, name: String): PsiElement? {
        if (element is PsiDirectory) {
            val file = element.findFile("$name.py")
            if (file != null) return file

            val subdir = element.findSubdirectory(name)
            if (subdir != null && subdir.findFile("__init__.py") != null) return subdir

            val init = element.findFile("__init__.py")
            if (init is PyFile) {
                return findMember(init, name)
            }
        }
        if (element is PyFile) {
            return element.findTopLevelClass(name) ?: element.findTopLevelFunction(name)
            ?: element.findTopLevelAttribute(
                name
            )
        }
        if (element is PyClass) {
            return element.findNestedClass(name, true) ?: element.findMethodByName(name, true, null)
            ?: element.findInstanceAttribute(name, true) ?: element.findClassAttribute(name, true, null)
        }
        return null
    }
}
