package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.generator

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression

private const val ANY_TEXT = "Any"

internal fun annotationUsesUnqualifiedAny(annotation: PyExpression?): Boolean {
    if (annotation == null) {
        return false
    }

    val refs = mutableListOf<PyReferenceExpression>()
    if (annotation is PyReferenceExpression) {
        refs.add(annotation)
    }
    refs.addAll(PsiTreeUtil.findChildrenOfType(annotation, PyReferenceExpression::class.java))
    if (refs.any { ref -> ref.name == ANY_TEXT && ref.qualifier == null }) {
        return true
    }

    val strings = mutableListOf<PyStringLiteralExpression>()
    if (annotation is PyStringLiteralExpression) {
        strings.add(annotation)
    }
    strings.addAll(PsiTreeUtil.findChildrenOfType(annotation, PyStringLiteralExpression::class.java))
    return strings.any { str -> stringLiteralUsesUnqualifiedAny(str.stringValue) }
}

private fun stringLiteralUsesUnqualifiedAny(text: String): Boolean {
    if (!text.contains(ANY_TEXT)) {
        return false
    }

    var index = 0
    while (index < text.length) {
        if (isAnyToken(text, index)) {
            return true
        }
        index++
    }

    return false
}

private fun isAnyToken(text: String, index: Int): Boolean {
    if (index + 3 > text.length) {
        return false
    }
    if (!text.regionMatches(index, ANY_TEXT, 0, 3, false)) {
        return false
    }

    val before = if (index == 0) null else text[index - 1]
    val after = if (index + 3 >= text.length) null else text[index + 3]
    if (before != null && (before == '.' || isIdentifierChar(before))) {
        return false
    }
    if (after != null && isIdentifierChar(after)) {
        return false
    }
    return true
}

private fun isIdentifierChar(c: Char): Boolean {
    return c.isLetterOrDigit() || c == '_'
}
