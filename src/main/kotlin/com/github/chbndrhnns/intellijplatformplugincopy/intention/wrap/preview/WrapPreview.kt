package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.preview

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyImportService
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyWrapHeuristics
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

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
        return buildDiffWithOptionalImport(file, element, modifiedText, ctorElement)
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
        return buildDiffWithOptionalImport(file, element, text, itemCtorElement)
    }

    fun buildWrapAllItemsInLiteral(
        file: PsiFile,
        container: PyExpression,
        itemCtorName: String,
        itemCtorElement: PsiNamedElement?
    ): IntentionPreviewInfo {
        val elements = when (container) {
            is PySequenceExpression -> container.elements
            is PySetLiteralExpression -> container.elements
            else -> emptyArray()
        }

        // We need to construct the new text for the container by wrapping each element
        // This is tricky because we need to preserve formatting/comments if possible, 
        // but for preview we can just reconstruct the string roughly or use text replacement.
        // Since preview is a diff, exact whitespace preservation isn't strictly required but nice.
        // However, we don't have a generator here easily without project.
        // We can simulate the text change.

        // Simple approach: iterate and build string.
        // Assumes standard formatting [a, b] -> [C(a), C(b)]

        val sb = StringBuilder()
        val containerText = container.text
        // If we just replace elements in the text... 
        // It's hard to do robustly on string level without parsing.
        // But maybe for preview it's enough to show "Wrap items..." result on a simplified view?
        // Or we can use the same approach as Applier if we had a copy of file?
        // IntentionPreviewInfo.CustomDiff allows providing new text.

        // Let's try to reconstruct:
        val prefix =
            if (container is PySetLiteralExpression) "{" else if (container is PyListLiteralExpression) "[" else if (container is PyTupleExpression) "(" else ""
        val suffix =
            if (container is PySetLiteralExpression) "}" else if (container is PyListLiteralExpression) "]" else if (container is PyTupleExpression) ")" else ""

        val joined = elements.joinToString(", ") { el ->
            if (!PyWrapHeuristics.isAlreadyWrappedWith(el, itemCtorName, itemCtorElement)) {
                "$itemCtorName(${el.text})"
            } else {
                el.text
            }
        }

        val newText = "$prefix$joined$suffix"
        return buildDiffWithOptionalImport(file, container, newText, itemCtorElement)
    }

    /**
     * Helper to build a CustomDiff preview, optionally prefixing the before/after with a single import line.
     */
    private fun buildDiffWithOptionalImport(
        file: PsiFile,
        originalElement: PyExpression,
        modifiedText: String,
        ctorElement: PsiNamedElement?
    ): IntentionPreviewInfo {
        val maybeImport = renderImportPreviewLine(file, originalElement, ctorElement)
        val before = buildString {
            if (maybeImport != null) append(maybeImport).append('\n')
            append(originalElement.text)
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
