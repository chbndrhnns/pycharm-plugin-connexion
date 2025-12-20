package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyCallableType
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

    fun getExpectedCallableType(
        expr: PyExpression,
        ctx: TypeEvalContext
    ): PyCallableType? {
        val info = ExpectedTypeInfo.getExpectedTypeInfo(expr, ctx)
        return info?.type as? PyCallableType
    }

    fun tryContainerItemCtor(element: PyExpression, ctx: TypeEvalContext): ExpectedCtor? =
        ContainerTyping.tryContainerItemCtor(element, ctx)

    fun expectedItemCtorsForContainer(expr: PyExpression, ctx: TypeEvalContext): List<ExpectedCtor> =
        ContainerTyping.expectedItemCtorsForContainer(expr, ctx)

    fun elementDisplaysAsCtor(element: PyExpression, expectedCtorName: String, ctx: TypeEvalContext): CtorMatch =
        ExpectedTypeInfo.elementDisplaysAsCtor(element, expectedCtorName, ctx)

    val CONTAINERS = setOf("list", "set", "tuple", "dict")

    fun getWrapperCallInfo(element: PyExpression): WrapperInfo? {
        val call = PyPsiUtils.flattenParens(element) as? PyCallExpression ?: return null
        val args = call.arguments
        if (args.size != 1) return null

        val callee = call.callee as? PyReferenceExpression ?: return null

        // Skip bound method calls like obj.get(...), obj.setdefault(...), etc.
        // These are not value-object wrappers and should not be unwrapped.
        if (callee.qualifier != null) return null

        val name = callee.name ?: return null
        var inner = args[0] ?: return null

        if (inner is PyKeywordArgument) {
            inner = inner.valueExpression ?: return null
        }

        return WrapperInfo(call, name, inner)
    }
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

data class WrapperInfo(
    val call: PyCallExpression,
    val name: String,
    val inner: PyExpression
)
