package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.apply

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyImportService
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyWrapHeuristics
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils

/**
 * Applies wrapping PSI edits and ensures necessary imports are present.
 *
 * This extracts and unifies the mutation logic previously duplicated in the intention
 * and its preview, so that a single source of truth drives both application and preview.
 */
class WrapApplier(
    private val imports: PyImportService = PyImportService()
) {
    /**
     * Apply wrapping for a concrete constructor name.
     * Adds import (if needed) using the original [element] as anchor, then replaces it.
     */
    fun apply(project: Project, file: PsiFile, element: PyExpression, ctorName: String, ctorElement: PsiNamedElement?) {
        WriteCommandAction.runWriteCommandAction(project) {
            // Ensure import first (before replacing), skip if builtin or already available
            imports.ensureImportedIfNeeded(file, element as PyTypedElement, ctorElement)

            val generator = PyElementGenerator.getInstance(project)
            val unwrapped = PyPsiUtils.flattenParens(element) ?: element

            val wrappedText = when {
                ctorName == "str" && unwrapped is PyNumericLiteralExpression -> "\"${unwrapped.text}\""
                ctorName == "list" && PyWrapHeuristics.isContainerExpression(unwrapped) -> "list(${element.text})"
                ctorName == "list" -> "[${unwrapped.text}]"
                else -> "$ctorName(${unwrapped.text})"
            }

            val wrapped = generator.createExpressionFromText(LanguageLevel.getLatest(), wrappedText)
            element.replace(wrapped)
        }
    }

    /**
     * Apply element-wise wrapping using a comprehension. Currently supports list comprehensions.
     */
    fun applyElementwise(
        project: Project,
        file: PsiFile,
        element: PyExpression,
        container: String,
        itemCtorName: String,
        itemCtorElement: PsiNamedElement?
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            // Ensure import for the item ctor if needed
            imports.ensureImportedIfNeeded(file, element as PyTypedElement, itemCtorElement)

            val generator = PyElementGenerator.getInstance(project)
            val srcText = (PyPsiUtils.flattenParens(element) ?: element).text
            val v = "v"
            val comp = when (container.lowercase()) {
                "list" -> "[${itemCtorName}($v) for $v in $srcText]"
                else -> "[${itemCtorName}($v) for $v in $srcText]" // safe fallback
            }
            val wrapped = generator.createExpressionFromText(LanguageLevel.getLatest(), comp)
            element.replace(wrapped)
        }
    }
}
