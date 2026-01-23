package com.github.chbndrhnns.betterpy.features.intentions.shared

import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression

/**
 * Shared call-site helpers for intentions working with Python call expressions.
 */
fun isPositionalOnlyCallable(call: PyCallExpression): Boolean {
    val callee = call.callee as? PyReferenceExpression ?: return false
    val resolved = callee.reference.resolve() as? PyFunction ?: return false

    // A function is considered positional-only if its parameter list syntax
    // contains the '/' separator, e.g. `def f(x, /, y): ...`.
    // This works both for source and for stub-based definitions.
    val paramList = resolved.parameterList
    return paramList.text.contains("/")
}
