package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.preview

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyImportService
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyWrapHeuristics
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Builds an intention preview diff for a given wrapping action.
 * Mirrors the transformation logic used by WrapApplier.
 */
class WrapPreview(
    private val imports: PyImportService = PyImportService()
) {
    fun build(
        file: PsiFile,
        element: PyExpression,
        ctorName: String,
        ctorElement: PsiNamedElement?
    ): IntentionPreviewInfo {
        val unwrapped = PyPsiUtils.flattenParens(element) ?: element
        val originalText = unwrapped.text
        val modifiedText = when (ctorName) {
            "str" if unwrapped is PyNumericLiteralExpression -> "\"$originalText\""
            "list" if PyWrapHeuristics.isContainerExpression(unwrapped) -> "list($originalText)"
            "list" -> "[$originalText]"
            else -> "$ctorName($originalText)"
        }

        val maybeImport = renderImportPreviewLine(file, element, ctorElement)
        val before = buildString {
            if (maybeImport != null) append(maybeImport).append('\n')
            append(element.text)
        }
        val after = buildString {
            if (maybeImport != null) append(maybeImport).append('\n')
            append(modifiedText)
        }

        return IntentionPreviewInfo.CustomDiff(
            file.fileType,
            file.name,
            before,
            after
        )
    }

    fun buildElementwise(
        file: PsiFile,
        element: PyExpression,
        container: String,
        itemCtorName: String,
        itemCtorElement: PsiNamedElement?
    ): IntentionPreviewInfo {
        val unwrapped = PyPsiUtils.flattenParens(element) ?: element
        val src = unwrapped.text
        val v = "v"
        val text = when (container.lowercase()) {
            "list" -> "[${itemCtorName}($v) for $v in $src]"
            else -> "[${itemCtorName}($v) for $v in $src]"
        }
        val maybeImport = renderImportPreviewLine(file, element, itemCtorElement)
        val before = buildString {
            if (maybeImport != null) append(maybeImport).append('\n')
            append(element.text)
        }
        val after = buildString {
            if (maybeImport != null) append(maybeImport).append('\n')
            append(text)
        }
        return IntentionPreviewInfo.CustomDiff(
            file.fileType,
            file.name,
            before,
            after
        )
    }

    /**
     * Computes a one-line import statement string to show in preview if an import would be added by the applier.
     * Returns null when no import is needed (already in scope/imported or builtin/unresolvable).
     */
    private fun renderImportPreviewLine(file: PsiFile, anchor: PyExpression, ctorElement: PsiNamedElement?): String? {
        ctorElement ?: return null

        // Skip builtins and already-imported names
        if (PyBuiltinCache.getInstance(anchor).isBuiltin(ctorElement)) return null
        if (imports.isImported(file, ctorElement.name ?: return null)) return null

        // If name already resolvable in scope, skip import
        val tec = TypeEvalContext.codeAnalysis(file.project, file)
        val name = ctorElement.name ?: return null
        // Reuse the same logic as applier: if resolvable, no import needed
        // We can't directly call the private resolve here; best-effort: rely on isImported + show canonical path

        // Find a canonical import path and render as a 'from X import Name'
        val qn = QualifiedNameFinder.findCanonicalImportPath(ctorElement, anchor) ?: return null
        val modulePath = qn.toString()
        if (modulePath.isBlank()) return null
        return "from $modulePath import $name"
    }
}
