package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

/**
 * Small collection of heuristics used by the wrap intention.
 */
object PyWrapHeuristics {
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
}
