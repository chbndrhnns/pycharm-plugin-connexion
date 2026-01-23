package com.github.chbndrhnns.betterpy.core.psi

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.*
import com.jetbrains.python.psi.PyStringLiteralExpression

class PyDottedSegmentReference(
    element: PyStringLiteralExpression,
    range: TextRange,
    private val fullPath: String,
    private val fullPathOffsetInElement: Int,
    private val resolveImported: Boolean = true
) : PsiPolyVariantReferenceBase<PyStringLiteralExpression>(element, range) {

    override fun handleElementRename(newElementName: String): PsiElement {
        val nameWithoutExt = newElementName.removeSuffix(".py")
        return super.handleElementRename(nameWithoutExt)
    }

    override fun bindToElement(element: PsiElement): PsiElement {
        return this.element
    }

    override fun isSoft(): Boolean = true

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        element.stringValueTextRange
        // fullPathOffsetInElement is the offset where 'fullPath' starts relative to element start.
        // rangeInElement is relative to element start.
        
        // We want the part of fullPath that corresponds to this reference.
        // rangeInElement.endOffset is the end of this segment in element.
        // fullPath starts at fullPathOffsetInElement.
        
        val segmentEndInPath = rangeInElement.endOffset - fullPathOffsetInElement
        if (segmentEndInPath < 0 || segmentEndInPath > fullPath.length) return ResolveResult.EMPTY_ARRAY
        
        val pathUpToSegment = fullPath.substring(0, segmentEndInPath)
        
        val resolved = PyResolveUtils.resolveDottedName(pathUpToSegment, element, resolveImported)
        return resolved.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun getVariants(): Array<Any> {
        val segmentStartInPath = rangeInElement.startOffset - fullPathOffsetInElement

        if (segmentStartInPath <= 0) {
            return topLevelVariants()
        }

        // Check for preceding dot
        if (fullPath[segmentStartInPath - 1] != '.') return emptyArray()
        
        val prefix = fullPath.substring(0, segmentStartInPath - 1)
        val parents = PyResolveUtils.resolveDottedName(prefix, element, resolveImported)
        val variants = mutableListOf<Any>()
        for (parent in parents) {
            variants.addAll(PyResolveUtils.getVariants(parent))
        }
        return variants.distinct().toTypedArray()
    }

    private fun topLevelVariants(): Array<Any> {
        val result = LinkedHashSet<Any>()
        val project = element.project
        val psiManager = PsiManager.getInstance(project)
        val fileIndex = ProjectFileIndex.getInstance(project)
        val rootManager = ProjectRootManager.getInstance(project)
        val includeSourceRootPrefix = PluginSettingsState.instance().state.enableRestoreSourceRootPrefix

        rootManager.contentSourceRoots.forEach { sourceRoot ->
            val psiDirectory = psiManager.findDirectory(sourceRoot) ?: return@forEach
            val contentRoot = fileIndex.getContentRootForFile(sourceRoot)
            val prefixPath = if (includeSourceRootPrefix && contentRoot != null) {
                VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '.')
            } else null

            if (!prefixPath.isNullOrEmpty()) {
                val firstComponent = prefixPath.substringBefore('.')
                result.add(LookupElementBuilder.create(firstComponent).withIcon(psiDirectory.getIcon(0)))
            } else {
                result.addAll(PyResolveUtils.getVariants(psiDirectory))
            }
        }

        return result.toTypedArray()
    }
}
