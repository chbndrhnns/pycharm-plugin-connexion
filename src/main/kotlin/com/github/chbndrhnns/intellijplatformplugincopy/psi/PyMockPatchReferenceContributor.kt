package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression

class PyMockPatchReferenceContributor : PsiReferenceContributor() {
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

                    if (isMockPatchArgument(literal)) {
                        refs.add(PyMockPatchReference(literal))
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
}

class PyMockPatchReference(element: PyStringLiteralExpression) :
    PsiPolyVariantReferenceBase<PyStringLiteralExpression>(element) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val resolved = PyResolveUtils.resolveDottedName(myElement.stringValue, myElement)
        if (resolved != null) {
            return arrayOf(PsiElementResolveResult(resolved))
        }
        return ResolveResult.EMPTY_ARRAY
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
