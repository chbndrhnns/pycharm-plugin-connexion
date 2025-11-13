package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Facade object exposing the stable API that other intentions/tests already call.
 * Internals are split across files by responsibility: CaretSelection, ExpectedTypeInfo, ContainerTyping.
 */
object PyTypeIntentions {

    // ---- Caret selection ----
    fun findExpressionAtCaret(editor: Editor, file: PsiFile) =
        CaretSelection.findExpressionAtCaret(editor, file)

    fun findContainerItemAtCaret(editor: Editor, containerOrElement: PyExpression) =
        CaretSelection.findContainerItemAtCaret(editor, containerOrElement)

    // ---- Type names / constructor resolution ----
    fun computeDisplayTypeNames(expr: PyExpression, ctx: TypeEvalContext) =
        ExpectedTypeInfo.computeDisplayTypeNames(expr, ctx)

    fun expectedCtorName(expr: PyExpression, ctx: TypeEvalContext): String? =
        ExpectedTypeInfo.expectedCtorName(expr, ctx)

    fun canonicalCtorName(element: PyTypedElement, ctx: TypeEvalContext): String? =
        ExpectedTypeInfo.canonicalCtorName(element, ctx)

    // ---- Container element typing helpers ----
    @Suppress("KDocUnresolvedReference")
    fun tryContainerItemCtor(element: PyExpression, ctx: TypeEvalContext): ExpectedCtor? =
        ContainerTyping.tryContainerItemCtor(element, ctx)

    // New: expected item ctor for a container-typed expression even when not inside the container
    fun expectedItemCtorForContainer(expr: PyExpression, ctx: TypeEvalContext): ExpectedCtor? =
        ContainerTyping.expectedItemCtorForContainer(expr, ctx)

    // New: all candidate item ctors for container-typed expression (union-aware)
    fun expectedItemCtorsForContainer(expr: PyExpression, ctx: TypeEvalContext): List<ExpectedCtor> =
        ContainerTyping.expectedItemCtorsForContainer(expr, ctx)

    fun elementDisplaysAsCtor(element: PyExpression, expectedCtorName: String, ctx: TypeEvalContext): CtorMatch =
        ExpectedTypeInfo.elementDisplaysAsCtor(element, expectedCtorName, ctx)
}

// Lightweight DTOs shared across the split modules stay at package level to avoid import churn.
data class TypeNames(
    val actual: String?,
    val expected: String?,
    val actualElement: PsiElement?,
    val expectedAnnotationElement: PyTypedElement?,
    val expectedElement: PsiNamedElement?
)

data class ExpectedCtor(
    val name: String,
    val symbol: PsiNamedElement?
)

enum class CtorMatch { MATCHES, DIFFERS }
