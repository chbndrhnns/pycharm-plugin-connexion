package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyStringLiteralExpression

class PyFilterWarningsReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyStringLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val literal = element as? PyStringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                    val refs = mutableListOf<PsiReference>()

                    if (isFilterWarningsArgument(literal)) {
                        refs.addAll(createFilterWarningsReferences(literal))
                    }

                    return refs.toTypedArray()
                }
            }
        )
    }

    private fun isFilterWarningsArgument(literal: PyStringLiteralExpression): Boolean {
        val argumentList = literal.parent as? PyArgumentList ?: return false
        val call = argumentList.parent as? PyCallExpression ?: return false

        val decorator = call.parent as? PyDecorator
        if (decorator != null) {
            val qName = decorator.qualifiedName
            if (qName != null && qName.toString().endsWith("filterwarnings")) {
                return true
            }
        }
        return false
    }

    private fun createFilterWarningsReferences(literal: PyStringLiteralExpression): List<PsiReference> {
        val text = literal.stringValue
        val parts = text.split(":")

        if (parts.size >= 3) {
            val category = parts[2]
            if (category.isNotEmpty()) {
                var offset = 0
                offset += parts[0].length + 1
                offset += parts[1].length + 1

                val start = offset
                val valueRange = literal.stringValueTextRange
                val startOffsetInElement = valueRange.startOffset + start

                if (startOffsetInElement + category.length <= literal.textLength) {
                    return listOf(
                        PyFilterWarningsCategoryReference(
                            literal,
                            TextRange(startOffsetInElement, startOffsetInElement + category.length),
                            category
                        )
                    )
                }
            }
        }
        return emptyList()
    }
}

class PyFilterWarningsCategoryReference(
    element: PyStringLiteralExpression,
    rangeInElement: TextRange,
    private val categoryName: String
) : PsiPolyVariantReferenceBase<PyStringLiteralExpression>(element, rangeInElement) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val resolved = PyResolveUtils.resolveDottedName(categoryName, myElement)
        if (resolved != null) {
            return arrayOf(PsiElementResolveResult(resolved))
        }
        return ResolveResult.EMPTY_ARRAY
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
