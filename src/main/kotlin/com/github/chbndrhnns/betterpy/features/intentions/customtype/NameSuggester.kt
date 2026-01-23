package com.github.chbndrhnns.betterpy.features.intentions.customtype

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
            val normalized = part.lowercase()
            normalized.replaceFirstChar { it.uppercaseChar() }
        }
    }

    /**
     * Derive a collection-style name from a field/parameter identifier when the
     * underlying builtin type is a container (e.g. ``list``).
     *
     * For the specific case we currently care about, this turns
     *
     *   ``files: list[str]``
     *
     * into a preferred class name ``Files`` so we generate
     * ``class Files(list): ...`` instead of a generic ``CustomList``.
     */
    fun deriveCollectionBaseName(identifier: String): String? {
        if (identifier.isEmpty()) return null

        // Keep the heuristic conservative: only accept simple lowercase names
        // (plus digits/underscores) to avoid surprising behaviour for
        // existing cases that rely on the generic CustomX naming.
        val isSimpleLowercase = identifier.all { it.isLowerCase() || it.isDigit() || it == '_' }
        if (!isSimpleLowercase) return null

        // For now we only special-case obvious plural-ish names that end in
        // ``s``. This covers the motivating example ``files`` while keeping
        // other short names like ``val`` or ``x`` on the old path.
        if (!identifier.endsWith('s')) return null

        return identifier.replaceFirstChar { it.uppercaseChar() }
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
