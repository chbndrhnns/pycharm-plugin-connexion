package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.CtorMatch
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.WrapperInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Small collection of heuristics used by the wrap intention.
 */
object PyWrapHeuristics {

    val CONTAINERS = setOf("list", "set", "tuple", "dict")

    fun isContainerLiteral(expr: PsiElement): Boolean = when (expr) {
        is PyListLiteralExpression,
        is PyTupleExpression,
        is PySetLiteralExpression,
        is PyDictLiteralExpression -> true

        else -> false
    }

    fun isInsideContainerLiteralOrComprehension(expr: PyExpression): Boolean {
        val p = expr.parent
        return isContainerLiteral(p) || when (p) {
            is PyListCompExpression,
            is PySetCompExpression,
            is PyDictCompExpression -> true

            else -> false
        }
    }

    fun shouldSuppressContainerCtor(element: PyExpression, ctorName: String): Boolean {
        if (ctorName.lowercase() !in CONTAINERS) return false
        return isInsideContainerLiteralOrComprehension(element)
    }

    fun parentMatchesExpectedContainer(element: PyExpression, expectedCtor: String): Boolean {
        val p = element.parent
        return when (expectedCtor.lowercase()) {
            "list" -> p is PyListLiteralExpression || p is PyListCompExpression
            "set" -> p is PySetLiteralExpression || p is PySetCompExpression
            "tuple" -> p is PyTupleExpression
            "dict" -> p is PyDictLiteralExpression || p is PyDictCompExpression
            else -> false
        }
    }

    /**
     * Determines whether the given expression is a container literal or a
     * comprehension/generator, in which case wrapping with list(expr) is preferred
     * over [expr] to preserve iteration semantics rather than nesting.
     */
    fun isContainerExpression(expr: PyExpression): Boolean = when (expr) {
        is PyListLiteralExpression,
        is PyTupleExpression,
        is PySetLiteralExpression,
        is PyDictLiteralExpression,
        is PyListCompExpression,
        is PySetCompExpression,
        is PyDictCompExpression,
        is PyGeneratorExpression -> true

        is PyCallExpression -> {
            val calleeName = (expr.callee as? PyReferenceExpression)?.name
            // Treat common iterable/container factories as containers to avoid nesting
            // e.g., prefer list(set()) over [set()] and list(range(...)) over [range(...)]
            calleeName in setOf("set", "tuple", "dict", "range")
        }

        else -> false
    }

    /**
     * Returns true if the given expression is already wrapped by a call to the same constructor
     * that we intend to suggest. Prevents multi-wrap like One(One("abc")).
     *
     * Improved over the original: also matches qualified calls (e.g., module.ctor(x)) and,
     * when [expected] is provided, compares the resolved callee symbol to ensure correctness.
     */
    fun isAlreadyWrappedWith(expr: PyExpression, ctorName: String, expected: PsiNamedElement?): Boolean {
        // Case A: the expression itself is a call to ctorName
        (expr as? PyCallExpression)?.let { call ->
            if (calleeMatches(call, ctorName, expected)) return true
        }

        // Case B: the expression is an argument of a call to ctorName
        val call = PsiTreeUtil.getParentOfType(expr, PyCallExpression::class.java)
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java)
        if (call != null && argList != null && PsiTreeUtil.isAncestor(argList, expr, false)) {
            if (calleeMatches(call, ctorName, expected)) return true
        }
        return false
    }

    private fun calleeMatches(call: PyCallExpression, ctorName: String, expected: PsiNamedElement?): Boolean {
        val callee = call.callee as? PyReferenceExpression ?: return false
        // Quick name check (handles both bare and qualified names by last component)
        val simpleName = callee.name
        if (simpleName == ctorName) return true

        // If we have an expected element, prefer precise resolution-based check
        if (expected != null) {
            val resolved = callee.reference.resolve() as? PsiNamedElement
            if (resolved != null && resolved == expected) return true
        }
        return false
    }

    fun elementMatchesCtor(element: PyExpression, ctor: ExpectedCtor, ctx: TypeEvalContext): Boolean {
        val symbol = ctor.symbol
        if (symbol is PyClass) {
            val actualType = ctx.getType(element)
            if (actualType is PyClassType) {
                val actualClass = actualType.pyClass

                // Check identity
                if (actualClass == symbol) return true

                // Check qualified name (robustness against PSI mismatch)
                val expectedQName = symbol.qualifiedName
                if (expectedQName != null) {
                    if (actualClass.qualifiedName == expectedQName) return true
                    // Check ancestors
                    if (actualClass.getAncestorClasses(ctx).any { it.qualifiedName == expectedQName }) return true
                }
            }
        }
        return PyTypeIntentions.elementDisplaysAsCtor(element, ctor.name, ctx) == CtorMatch.MATCHES
    }

    fun getWrapperCallInfo(element: PyExpression): WrapperInfo? =
        PyTypeIntentions.getWrapperCallInfo(element)

    fun expectedCtorName(expr: PyExpression, ctx: TypeEvalContext): String? =
        PyTypeIntentions.expectedCtorName(expr, ctx)

    fun expectedItemCtorsForContainer(expr: PyExpression, ctx: TypeEvalContext): List<ExpectedCtor> =
        PyTypeIntentions.expectedItemCtorsForContainer(expr, ctx)
}
