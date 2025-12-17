package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.python.psi.PyFile

class ConnexionControllerReference(
    element: PsiElement,
    range: TextRange,
    private val prefixPath: String
) : PsiReferenceBase<PsiElement>(element, range) {

    override fun resolve(): PsiElement? {
        val myName = rangeInElement.substring(element.text)
        val fullPath = if (prefixPath.isEmpty()) myName else "$prefixPath.$myName"
        return resolvePath(element.project, fullPath)
    }

    override fun getVariants(): Array<Any> {
        val parentItem = resolvePath(element.project, prefixPath) ?: return emptyArray()

        if (parentItem is com.intellij.psi.PsiDirectory) {
            val dirs = parentItem.subdirectories.map { it.name }
            val files = parentItem.files
                .filter { it is PyFile && it.name != "__init__.py" }
                .map { it.name.removeSuffix(".py") }
            return (dirs + files).toTypedArray()
        }
        return emptyArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        // newElementName is the new name of the file (e.g. "new_api.py") or directory ("new_pkg")
        val nameWithoutExt = newElementName.removeSuffix(".py")
        return super.handleElementRename(nameWithoutExt)
    }
}

fun resolvePath(project: Project, path: String): PsiFileSystemItem? {
    val baseDir =
        com.intellij.openapi.roots.ProjectRootManager.getInstance(project).contentRoots.firstOrNull() ?: return null
    var current: PsiFileSystemItem? = PsiManager.getInstance(project).findDirectory(baseDir) ?: return null

    if (path.isEmpty()) return current

    val parts = path.split(".")
    for (part in parts) {
        if (current is com.intellij.psi.PsiDirectory) {
            val subDir = current.findSubdirectory(part)
            if (subDir != null) {
                current = subDir
                continue
            }
            val file = current.findFile("$part.py")
            if (file is PyFile) {
                current = file
                continue
            }
            return null
        } else {
            // Cannot traverse into a file
            return null
        }
    }
    return current
}
