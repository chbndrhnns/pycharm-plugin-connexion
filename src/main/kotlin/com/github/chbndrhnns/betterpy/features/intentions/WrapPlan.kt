package com.github.chbndrhnns.betterpy.features.intentions

import com.github.chbndrhnns.betterpy.features.intentions.shared.ExpectedCtor
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.PyExpression

sealed interface WrapPlan {
    val element: PyExpression
}

data class Single(
    override val element: PyExpression,
    val ctorName: String,
    val ctorElement: PsiNamedElement?
) : WrapPlan

data class UnionChoice(
    override val element: PyExpression,
    val candidates: List<ExpectedCtor>
) : WrapPlan

data class ElementwiseUnionChoice(
    override val element: PyExpression,
    val container: String,
    val candidates: List<ExpectedCtor>
) : WrapPlan

// Element-wise wrapping plan (e.g., list[Item] expected, wrap as [Item(v) for v in src])
data class Elementwise(
    override val element: PyExpression,
    val container: String, // e.g., "list"
    val itemCtorName: String,
    val itemCtorElement: PsiNamedElement?
) : WrapPlan

data class ReplaceWithVariant(
    override val element: PyExpression,
    val variantName: String,
    val variantElement: PsiNamedElement?
) : WrapPlan

data class LambdaWrap(
    override val element: PyExpression,
    val params: String
) : WrapPlan
