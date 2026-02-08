package com.github.chbndrhnns.betterpy.features.intentions.shared

import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.types.*


/**
 * Stable, compact, and configurable stringification of PyType trees.
 *
 * Note: Uses only widely available PyType interfaces to avoid SDK version mismatches.
 */
object TypeNameRenderer {
    data class Options(
        val usePep604: Boolean = true,
        val qualifyClasses: Boolean = false,
        val maxTupleItems: Int? = 4
    )

    fun render(type: PyType?, options: Options = Options()): String {
        val t = type.unwrapAnnotated()
        return when (t) {
            null -> "Unknown"
            is PyUnionType -> renderUnion(t, options)
            is PyTupleType -> renderTuple(t, options)
            is PyCollectionType -> renderCollection(t, options)
            is PyClassType -> if (isNoneType(t)) "None" else renderClass(t, options)
            else -> t.name ?: "Unknown"
        }
    }

    fun getAnnotatedType(resolved: PsiElement, context: TypeEvalContext): PyType? {
        if (resolved is PySubscriptionExpression) {
            val operand = resolved.operand
            if (resolvesToQualifiedNames(operand, context, "typing.Annotated", "typing_extensions.Annotated")) {
                val indexExpr = resolved.indexExpression
                val typeExpr = if (indexExpr is PyTupleExpression) indexExpr.elements.firstOrNull() else indexExpr
                if (typeExpr != null) {
                    return context.getType(typeExpr)
                }
            }
        }

        return null
    }

    private fun resolvesToQualifiedNames(
        operand: PyExpression,
        context: TypeEvalContext,
        vararg names: String
    ): Boolean {
        val type = context.getType(operand)
        if (type is PyClassType) {
            val qName = type.classQName
            return qName != null && names.any { it == qName }
        }
        return false
    }

    // --- helpers ---

    private fun isNoneType(type: PyType?): Boolean {
        if (type !is PyClassType) return false
        val qName = type.classQName
        return qName != null && PyNames.NONE.contains(qName)
    }

    private fun PyType?.unwrapAnnotated(): PyType? {
        val t = this ?: return null
        // Rely on the presence of a dedicated annotated-type implementation without using reflection.
        // We assume that when the Python plugin supports Annotated, it exposes it as a subtype of PyType
        // that also implements PyCollectionType where the first element type is the underlying T in
        // Annotated[T, ...]. Older SDKs that lack such a type will simply not match this pattern.

        val col = t as? PyCollectionType ?: return t
        val qName = col.classQName ?: col.name ?: return t
        val short = qName.substringAfterLast('.')
        val looksAnnotated = short == "Annotated" || qName.contains("typing.Annotated")
        if (!looksAnnotated) return t

        val params = col.elementTypes.toList()
        if (params.isEmpty()) return t
        return params.firstOrNull() ?: t
    }

    private fun renderUnion(union: PyUnionType, o: Options): String {
        // Flatten nested unions into a single ordered set of parts, tracking None separately
        val parts = LinkedHashSet<String>()
        var hasNone = false

        fun collect(t: PyType?) {
            when (t) {
                null -> {}
                is PyUnionType -> t.members.forEach { collect(it) }
                else -> {
                    if (isNoneType(t)) {
                        hasNone = true
                    } else {
                        parts += render(t, o)
                    }
                }
            }
        }
        union.members.forEach { collect(it) }

        val items = parts.toMutableList()
        if (hasNone) items += "None"

        return if (o.usePep604) items.joinToString(" | ")
        else "Union[${items.joinToString(", ")}]"
    }

    private fun renderCollection(col: PyCollectionType, o: Options): String {
        val name = (col.classQName) ?: col.name ?: "Collection"
        val display = builtinAlias(name)
        val params = col.elementTypes.map { render(it, o) }

        return if (params.isEmpty()) display else "$display[${params.joinToString(", ")}]"
    }

    private fun renderTuple(tuple: PyTupleType, o: Options): String {
        val elts = tuple.elementTypes
        if (elts.isEmpty()) return "tuple[]"

        val items = elts.map { render(it, o) }
        val compact = o.maxTupleItems
        return if (compact != null && items.size > compact) {
            "tuple[${items.first()}, ...]"
        } else {
            "tuple[${items.joinToString(", ")}]"
        }
    }

    private fun renderClass(cls: PyClassType, o: Options): String {
        val base = if (o.qualifyClasses) {
            cls.classQName ?: (cls.name ?: "object")
        } else {
            cls.name ?: "object"
        }

        // If it also exposes collection params via PyCollectionType, prefer that rendering
        if (cls is PyCollectionType) return renderCollection(cls, o)

        return normalizeBuiltin(base.substringAfterLast('.'))
    }

    private fun builtinAlias(name: String): String = when (name) {
        // Map typing to builtin-likes
        "typing.List", "List", "list" -> "list"
        "typing.Dict", "Dict", "dict" -> "dict"
        "typing.Set", "Set", "set" -> "set"
        "typing.Tuple", "Tuple", "tuple" -> "tuple"
        "typing.FrozenSet", "FrozenSet", "frozenset" -> "frozenset"
        else -> normalizeBuiltin(name.substringAfterLast('.'))
    }

    private fun normalizeBuiltin(short: String): String = when (short) {
        "NoneType" -> "None"
        else -> short
    }
}
