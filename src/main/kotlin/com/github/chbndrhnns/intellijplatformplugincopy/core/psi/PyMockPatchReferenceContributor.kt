package com.github.chbndrhnns.intellijplatformplugincopy.core.psi

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression

class PyMockPatchReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        if (!PluginSettingsState.instance().state.enablePyMockPatchReferenceContributor) return
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyStringLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val literal = element as? PyStringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                    val refs = mutableListOf<PsiReference>()

                    if (isMockPatchArgument(literal)) {
                        refs.addAll(createReferences(literal))
                    }

                    return refs.toTypedArray()
                }
            }
        )
    }

    private fun isMockPatchArgument(literal: PyStringLiteralExpression): Boolean {
        val argumentList = literal.parent as? PyArgumentList ?: return false
        val call = argumentList.parent as? PyCallExpression ?: return false
        val callee = call.callee as? PyReferenceExpression ?: return false

        val arguments = argumentList.arguments
        if (arguments.isNotEmpty() && arguments[0] === literal) {
            val name = callee.name
            if (name == "patch") return true
        }
        return false
    }

    private fun createReferences(literal: PyStringLiteralExpression): List<PsiReference> {
        val text = literal.stringValue
        val valueRange = literal.stringValueTextRange
        val startOffset = valueRange.startOffset

        val refs = mutableListOf<PsiReference>()

        if (text.isEmpty()) {
            val end = minOf(startOffset + 1, literal.textLength)
            refs.add(PyDottedSegmentReference(literal, TextRange(startOffset, end), text, startOffset))
            return refs
        }

        var currentIndex = 0

        while (currentIndex < text.length) {
            val nextDot = text.indexOf('.', currentIndex)
            val end = if (nextDot == -1) text.length else nextDot

            val range = TextRange(startOffset + currentIndex, startOffset + end)
            refs.add(PyDottedSegmentReference(literal, range, text, startOffset))

            currentIndex = end + 1
        }

        if (text.last() == '.') {
            val edge = startOffset + text.length
            val end = minOf(edge + 1, literal.textLength)
            refs.add(PyDottedSegmentReference(literal, TextRange(edge, end), text, startOffset))
        }
        return refs
    }
}
