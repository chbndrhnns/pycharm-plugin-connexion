package com.github.chbndrhnns.intellijplatformplugincopy.imports

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.resolve.PyCanonicalPathProvider

class SpecificImportPathProvider : PyCanonicalPathProvider {
    override fun getCanonicalPath(
        symbol: PsiElement?,
        qName: QualifiedName,
        foothold: PsiElement?
    ): QualifiedName? {
        if (symbol == null) return null

        // Determine the context element (foothold) for resolution
        val context = foothold ?: symbol

        // 1. Identify the actual file defining the symbol
        //    (This bypasses the logic that might have promoted 'symbol' to its package directory)
        val fileSystemItem: PsiFileSystemItem = if (symbol is PsiFileSystemItem) {
            symbol
        } else {
            symbol.containingFile ?: return null
        }

        val virtualFile = fileSystemItem.virtualFile ?: return null

        // 2. Ensure we only modify behavior for project source files.
        //    We generally want to respect re-exports in libraries (e.g. unittest),
        //    but strictly use specific paths in our own code to avoid circular imports.
        val project = context.project
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        if (!fileIndex.isInSourceContent(virtualFile)) {
            return null
        }

        // 3. Calculate the shortest importable QName for the *specific* file
        // We construct the QName manually to ensure we get the specific file path (e.g. 'domain._lib')
        // instead of the promoted package path (e.g. 'domain') which QualifiedNameFinder might prefer.
        val parent = virtualFile.parent ?: return null
        val packageName = fileIndex.getPackageNameByDirectory(parent) ?: return null
        val fileName = virtualFile.nameWithoutExtension

        // If we are in __init__.py, the specific path IS the package path, so let default logic handle it.
        if (fileName == "__init__") return null

        val specificQNameString = if (packageName.isEmpty()) fileName else "$packageName.$fileName"
        val specificQName = QualifiedName.fromDottedString(specificQNameString)

        // 4. If the specific QName is different (e.g. 'src.domain._lib') from the 
        //    proposed qName (e.g. 'src.domain'), return the specific one.
        if (specificQName != qName) {
            return specificQName
        }

        return null
    }
}
