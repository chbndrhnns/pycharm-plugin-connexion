package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

/** Container analysis and expected-constructor selection for container items. */
internal object ContainerTyping {

    // Public entry used by facade
    fun tryContainerItemCtor(element: PyExpression, ctx: TypeEvalContext): ExpectedCtor? {
        val container = findEnclosingContainer(element) ?: return null
        val pos = locatePositionInContainer(element) ?: return null
        return expectedCtorForContainerItem(pos, container, ctx)
    }

    // ---- Model ----
    private sealed interface ContainerPos {
        data object Item : ContainerPos
        data class TupleItem(val index: Int) : ContainerPos
        data object DictKey : ContainerPos
        data object DictValue : ContainerPos
    }

    private sealed interface ContainerKind {
        data object ListLit : ContainerKind
        data object SetLit : ContainerKind
        data class TupleLit(val size: Int) : ContainerKind
        data object DictLit : ContainerKind
        data object ListComp : ContainerKind
        data object SetComp : ContainerKind
        data object DictComp : ContainerKind
    }

    private data class ContainerContext(
        val container: PyExpression,
        val kind: ContainerKind,
        val pos: ContainerPos
    )

    // ---- Discovery ----
    private fun PsiElement.isAncestorOrSelfOf(other: PsiElement): Boolean =
        this == other || PsiTreeUtil.isAncestor(this, other, false)

    private fun findNearestContainer(el: PyExpression): PyExpression? =
        PsiTreeUtil.getParentOfType(
            el,
            PyListLiteralExpression::class.java,
            PySetLiteralExpression::class.java,
            PyTupleExpression::class.java,
            PyDictLiteralExpression::class.java,
            PyListCompExpression::class.java,
            PySetCompExpression::class.java,
            PyDictCompExpression::class.java
        )

    private fun classifyContainer(container: PyExpression): ContainerKind = when (container) {
        is PyListLiteralExpression -> ContainerKind.ListLit
        is PySetLiteralExpression -> ContainerKind.SetLit
        is PyTupleExpression -> ContainerKind.TupleLit(container.elements.size)
        is PyDictLiteralExpression -> ContainerKind.DictLit
        is PyListCompExpression -> ContainerKind.ListComp
        is PySetCompExpression -> ContainerKind.SetComp
        is PyDictCompExpression -> ContainerKind.DictComp
        else -> error("Unsupported container: ${container::class.java.simpleName}")
    }

    private fun locateElementPosition(container: PyExpression, element: PyExpression): ContainerPos? =
        when (container) {
            is PyListLiteralExpression, is PySetLiteralExpression -> ContainerPos.Item
            is PyTupleExpression -> {
                val idx = container.elements.indexOfFirst { it.isAncestorOrSelfOf(element) }
                if (idx >= 0) ContainerPos.TupleItem(idx) else null
            }

            is PyDictLiteralExpression -> {
                val kv = PsiTreeUtil.getParentOfType(element, PyKeyValueExpression::class.java) ?: return null
                when {
                    kv.key.isAncestorOrSelfOf(element) -> ContainerPos.DictKey
                    kv.value?.isAncestorOrSelfOf(element) == true -> ContainerPos.DictValue
                    else -> null
                }
            }

            is PyListCompExpression, is PySetCompExpression -> ContainerPos.Item
            is PyDictCompExpression -> ContainerPos.DictValue
            else -> null
        }

    private fun analyzeContainer(element: PyExpression): ContainerContext? {
        val c = findNearestContainer(element) ?: return null
        val pos = locateElementPosition(c, element) ?: return null
        val kind = classifyContainer(c)
        return ContainerContext(c, kind, pos)
    }

    private fun findEnclosingContainer(element: PyExpression): PyExpression? =
        analyzeContainer(element)?.container

    private fun locatePositionInContainer(element: PyExpression): ContainerPos? =
        analyzeContainer(element)?.pos

    // ---- Expected constructor selection ----
    private fun expectedCtorForContainerItem(
        pos: ContainerPos,
        container: PyExpression,
        ctx: TypeEvalContext
    ): ExpectedCtor? {
        val cc = ContainerContext(container, classifyContainer(container), pos)
        return expectedCtorFor(ctx, cc)
    }

    private sealed interface GenericShape {
        data object One : GenericShape
        data object TwoKV : GenericShape
        data class TupleN(val n: Int) : GenericShape
    }

    private enum class TypingBase { LIST, SET, SEQUENCE, COLLECTION, ITERABLE, MUTABLE_SEQUENCE, TUPLE, DICT, MAPPING, MUTABLE_MAPPING }

    private val NAME_TO_BASE: Map<String, TypingBase> = mapOf(
        "list" to TypingBase.LIST,
        "set" to TypingBase.SET,
        "sequence" to TypingBase.SEQUENCE,
        "collection" to TypingBase.COLLECTION,
        "iterable" to TypingBase.ITERABLE,
        "mutablesequence" to TypingBase.MUTABLE_SEQUENCE,
        "tuple" to TypingBase.TUPLE,
        "dict" to TypingBase.DICT,
        "mapping" to TypingBase.MAPPING,
        "mutablemapping" to TypingBase.MUTABLE_MAPPING,
    )

    private fun shapeOf(base: TypingBase, arg: PyExpression): GenericShape = when (base) {
        TypingBase.TUPLE -> {
            val tuple = arg as? PyTupleExpression
            GenericShape.TupleN(tuple?.elements?.size ?: 1)
        }

        TypingBase.DICT, TypingBase.MAPPING, TypingBase.MUTABLE_MAPPING -> GenericShape.TwoKV
        else -> GenericShape.One
    }

    private fun pickTypeArgFor(pos: ContainerPos, shape: GenericShape, arg: PyExpression): PyExpression? =
        when (shape) {
            GenericShape.One -> singleArg(arg)
            GenericShape.TwoKV -> when (pos) {
                is ContainerPos.DictKey -> (arg as? PyTupleExpression)?.elements?.getOrNull(0)
                is ContainerPos.DictValue, ContainerPos.Item -> (arg as? PyTupleExpression)?.elements?.getOrNull(1)
                is ContainerPos.TupleItem -> null
            }

            is GenericShape.TupleN -> when (pos) {
                is ContainerPos.TupleItem -> (arg as? PyTupleExpression)?.elements?.getOrNull(pos.index)
                else -> singleArg(arg)
            }
        }

    private fun expectedCtorFor(ctx: TypeEvalContext, cc: ContainerContext): ExpectedCtor? {
        val info = ExpectedTypeInfo.getExpectedTypeInfo(cc.container, ctx) ?: return null
        val annExpr = info.annotationExpr as? PyExpression ?: return null
        val sub = annExpr as? PySubscriptionExpression ?: return null

        val baseName = (sub.operand as? PyReferenceExpression)?.name?.lowercase() ?: return null
        val base = NAME_TO_BASE[baseName] ?: return null
        val indexExpr = sub.indexExpression ?: return null
        val shape = shapeOf(base, indexExpr)
        val chosen = pickTypeArgFor(cc.pos, shape, indexExpr) as? PyTypedElement ?: return null

        val ctor = ExpectedTypeInfo.canonicalCtorName(chosen, ctx) ?: return null
        val named = (chosen as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
        return ExpectedCtor(ctor, named)
    }

    private fun singleArg(arg: PyExpression): PyExpression? = when (arg) {
        is PyTupleExpression -> arg.elements.firstOrNull()
        else -> arg
    }
}
