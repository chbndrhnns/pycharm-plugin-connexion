package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.jetbrains.python.psi.PyFile

/**
 * Naming utilities for the "Introduce custom type from stdlib" feature.
 *
 * The behaviour mirrors the logic that previously lived in
 * IntroduceCustomTypeFromStdlibIntention: builtin-specific mappings for the
 * default "CustomX" name and a conservative identifier-to-PascalCase
 * conversion for preferred names.
 */
class NameSuggester {

    /** Derive a PascalCase base name from a snake_case identifier. */
    fun deriveBaseName(identifier: String): String? {
        if (!identifier.contains('_')) return null

        val parts = identifier.split('_').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        return parts.joinToString(separator = "") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
        }
    }

    /**
     * Suggest a type name given the builtin and an optional preferred base
     * name derived from context (e.g. `product_id` -> `ProductId`).
     */
    fun suggestTypeName(builtin: String, preferred: String?): String {
        if (preferred != null) return preferred

        val capitalizedBuiltin = when (builtin) {
            "int" -> "Int"
            "str" -> "Str"
            "float" -> "Float"
            "bool" -> "Bool"
            "bytes" -> "Bytes"
            "list" -> "List"
            "set" -> "Set"
            "dict" -> "Dict"
            "tuple" -> "Tuple"
            "frozenset" -> "FrozenSet"
            else -> builtin.replaceFirstChar { it.uppercaseChar() }
        }
        return "Custom" + capitalizedBuiltin
    }

    /** Ensure the suggested name does not clash with an existing top-level class in the file. */
    fun ensureUnique(pyFile: PyFile, base: String): String {
        val usedNames = pyFile.topLevelClasses.mapNotNull { it.name }.toMutableSet()
        if (!usedNames.contains(base)) return base

        var index = 1
        var candidate = "${base}${index}"
        while (usedNames.contains(candidate)) {
            index++
            candidate = "${base}${index}"
        }
        return candidate
    }
}
