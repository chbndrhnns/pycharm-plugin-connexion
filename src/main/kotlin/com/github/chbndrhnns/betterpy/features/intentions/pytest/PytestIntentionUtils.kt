package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PySequenceExpression

fun isPytestParametrizeOrFixtureParams(sequence: PySequenceExpression): Boolean {
    val parent = sequence.parent

    // Case 1: ArgumentList of a CallExpression
    if (parent is PyArgumentList) {
        val call = parent.parent as? PyCallExpression ?: return false
        val calleeText = call.callee?.text ?: return false

        if (calleeText.endsWith("parametrize")) {
            // Usually 2nd argument. Since checking index is brittle with kw args, assume if it's in arg list it's likely the value list
            // unless the user put it as first arg (argnames). 'argnames' is string or list of strings.
            // If sequence is list of strings, it might be ambiguous.
            // But generally users put [values] as second arg.
            // Let's assume yes.
            return true
        }

        if (calleeText.endsWith("fixture")) {
            // 'params' must be passed as keyword argument if in argument list? No, fixture(params=...)
            // If passed positionally: fixture(scope, params, ...). params is 2nd?
            // Documentation says: fixture(scope='function', params=None, ...)
            // So params is usually kwarg.
            // If we are here, it's a positional arg. Unlikely for 'params'.
            return false
        }
    }

    // Case 2: KeywordArgument
    if (parent is PyKeywordArgument) {
        if (parent.keyword == "params") {
            val argList = parent.parent as? PyArgumentList ?: return false
            val call = argList.parent as? PyCallExpression ?: return false
            val calleeText = call.callee?.text ?: return false
            if (calleeText.endsWith("fixture")) {
                return true
            }
        }
        if (parent.keyword == "argvalues") {
            val argList = parent.parent as? PyArgumentList ?: return false
            val call = argList.parent as? PyCallExpression ?: return false
            val calleeText = call.callee?.text ?: return false
            if (calleeText.endsWith("parametrize")) {
                return true
            }
        }
    }

    return false
}
