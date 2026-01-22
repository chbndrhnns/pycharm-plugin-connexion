package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyDocStringOwner
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyStringLiteralExpression

/** True if this string literal *is* the docstring of a module, class, or function. */
fun PyStringLiteralExpression.isDocStringLiteral(): Boolean {
    // Docstrings in Python PSI are represented as a PyExpressionStatement
    val exprStmt = parent as? PyExpressionStatement ?: return false

    // Find the nearest enclosing PyDocStringOwner: PyFile, PyClass, or PyFunction
    val owner: PyDocStringOwner = PsiTreeUtil.getParentOfType(
        this,
        PyDocStringOwner::class.java,
        /* strict = */ false
    ) ?: return false

    // Compare with the docstring expression reported by the owner
    val docExpr = owner.docStringExpression

    return docExpr === this && exprStmt === docExpr.parent
}
