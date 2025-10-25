package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.jetbrains.python.PyNames
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

    fun render(type: PyType?, options: Options = Options()): String =
        when (type) {
            null -> "Unknown"
            is PyUnionType -> renderUnion(type, options)
            is PyTupleType -> renderTuple(type, options)
            is PyCollectionType -> renderCollection(type, options)
            is PyClassType -> if (isNoneType(type)) "None" else renderClass(type, options)
            else -> type.name ?: "Unknown"
        }

    // --- helpers ---

    private fun isNoneType(type: PyType?): Boolean {
        if (type !is PyClassType) return false
        val qName = type.classQName
        return qName != null && PyNames.NONE.contains(qName)
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
        val params = col.elementTypes?.map { render(it, o) } ?: emptyList()

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
