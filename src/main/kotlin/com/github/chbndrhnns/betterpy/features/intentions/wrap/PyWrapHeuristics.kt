package com.github.chbndrhnns.betterpy.features.intentions.wrap

import com.github.chbndrhnns.betterpy.features.intentions.shared.CtorMatch
import com.github.chbndrhnns.betterpy.features.intentions.shared.ExpectedCtor
import com.github.chbndrhnns.betterpy.features.intentions.shared.PyTypeIntentions
import com.github.chbndrhnns.betterpy.features.intentions.shared.WrapperInfo
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.codeInsight.typing.isProtocol as builtInIsProtocol

/**
 * Small collection of heuristics used by the wrap intention.
 * 
 * Container detection methods delegate to [ContainerDetector] for consistency.
 */
object PyWrapHeuristics {

    val CONTAINERS get() = ContainerDetector.CONTAINER_NAMES

    fun isInsideContainerLiteralOrComprehension(expr: PyExpression): Boolean =
        ContainerDetector.isInsideContainerLiteralOrComprehension(expr)

    fun shouldSuppressContainerCtor(element: PyExpression, ctorName: String): Boolean =
        ContainerDetector.shouldSuppressContainerCtor(element, ctorName)

    fun parentMatchesExpectedContainer(element: PyExpression, expectedCtor: String): Boolean =
        ContainerDetector.parentMatchesExpectedContainer(element, expectedCtor)

    /**
     * Determines whether the given expression is a container literal or a
     * comprehension/generator, in which case wrapping with list(expr) is preferred
     * over [expr] to preserve iteration semantics rather than nesting.
     */
    fun isContainerExpression(expr: PyExpression): Boolean = ContainerDetector.isContainerExpression(expr)

    /**
     * Returns true if the given element represents a Protocol class.
     */
    fun isProtocol(symbol: PsiNamedElement?, context: TypeEvalContext): Boolean {
        val pyClass = symbol as? PyClass ?: return false
        val classType = context.getType(pyClass) as? PyClassType

        if (classType != null && builtInIsProtocol(classType, context)) {
            return true
        }

        return pyClass.getAncestorClasses(context).any { it.name == "Protocol" }
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
