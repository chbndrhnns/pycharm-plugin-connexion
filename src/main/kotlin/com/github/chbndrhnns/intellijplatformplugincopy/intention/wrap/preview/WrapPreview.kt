package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.preview

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyWrapHeuristics
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.impl.PyPsiUtils

/**
 * Builds an intention preview diff for a given wrapping action.
 * Mirrors the transformation logic used by WrapApplier.
 */
class WrapPreview {
    fun build(file: PsiFile, element: PyExpression, ctorName: String): IntentionPreviewInfo {
        val unwrapped = PyPsiUtils.flattenParens(element) ?: element
        val originalText = unwrapped.text
        val modifiedText = when {
            ctorName == "str" && unwrapped is PyNumericLiteralExpression -> "\"$originalText\""
            ctorName == "list" && PyWrapHeuristics.isContainerExpression(unwrapped) -> "list($originalText)"
            ctorName == "list" -> "[$originalText]"
            else -> "$ctorName($originalText)"
        }

        return IntentionPreviewInfo.CustomDiff(
            file.fileType,
            file.name,
            element.text,
            modifiedText
        )
    }
}
