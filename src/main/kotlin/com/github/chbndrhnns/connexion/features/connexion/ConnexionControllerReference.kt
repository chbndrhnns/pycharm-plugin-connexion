package com.github.chbndrhnns.connexion.features.connexion

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.python.psi.PyFile

class ConnexionControllerReference(
    element: PsiElement,
    range: TextRange,
    private val prefixPath: String
) : PsiReferenceBase<PsiElement>(element, range, true) {

    override fun resolve(): PsiElement? {
        val myName = rangeInElement.substring(element.text)
        val fullPath = if (prefixPath.isEmpty()) myName else "$prefixPath.$myName"
        return OpenApiSpecUtil.resolvePath(element.project, fullPath)
    }

    override fun getVariants(): Array<Any> {
        val parentItem = OpenApiSpecUtil.resolvePath(element.project, prefixPath) ?: return emptyArray()

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

