package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.jetbrains.python.psi.PyStringLiteralExpression

class PyDottedSegmentReference(
    element: PyStringLiteralExpression,
    range: TextRange,
    private val fullPath: String,
    private val fullPathOffsetInElement: Int,
    private val resolveImported: Boolean = true
) : PsiPolyVariantReferenceBase<PyStringLiteralExpression>(element, range) {

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
        return if (resolved != null) arrayOf(PsiElementResolveResult(resolved)) else ResolveResult.EMPTY_ARRAY
    }

    override fun getVariants(): Array<Any> {
        val segmentStartInPath = rangeInElement.startOffset - fullPathOffsetInElement
        
        if (segmentStartInPath <= 0) {
            // First segment or invalid - top-level module completion not supported
            return emptyArray()
        }
        
        // Check for preceding dot
        if (fullPath[segmentStartInPath - 1] != '.') return emptyArray()
        
        val prefix = fullPath.substring(0, segmentStartInPath - 1)
        val parent = PyResolveUtils.resolveDottedName(prefix, element, resolveImported)
        return PyResolveUtils.getVariants(parent)
    }
}
