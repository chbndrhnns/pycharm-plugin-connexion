package com.github.chbndrhnns.betterpy.features.imports

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.resolve.PyCanonicalPathProvider

class SourceRootPrefixProvider : PyCanonicalPathProvider {
    override fun getCanonicalPath(
        symbol: PsiElement?,
        qName: QualifiedName,
        foothold: PsiElement?
    ): QualifiedName? {
        if (!PluginSettingsState.instance().state.enableRestoreSourceRootPrefix) {
            return null
        }

        val virtualFile = PsiUtilCore.getVirtualFile(symbol) ?: return null
        val project = symbol?.project ?: return null

        val fileIndex = ProjectFileIndex.getInstance(project)
        val root = fileIndex.getSourceRootForFile(virtualFile) ?: return null
        val contentRoot = fileIndex.getContentRootForFile(virtualFile) ?: return null

        // specific optimization: if roots are same, no prefix needed
        if (root == contentRoot) {
            return null
        }

        // Ensure we are in a nested structure
        if (!VfsUtilCore.isAncestor(contentRoot, root, true)) {
            return null
        }

        // VfsUtilCore.getRelativePath returns path from ancestor (contentRoot) to file (root)
        val prefixPath = VfsUtilCore.getRelativePath(root, contentRoot)
        if (prefixPath.isNullOrEmpty()) {
            return null
        }

        val components = prefixPath.split('/')
        val prefix = QualifiedName.fromComponents(components)

        return prefix.append(qName)
    }
}