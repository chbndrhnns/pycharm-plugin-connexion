package com.github.chbndrhnns.betterpy.features.intentions.shared

/**
 * Utility object for checking Python builtin type names.
 * Consolidates the duplicate `isBuiltinName` functions that existed in
 * PopulateArgumentsService and PyValueGenerator.
 */
object PyBuiltinNames {
    private val BUILTIN_NAMES = setOf(
        "int", "str", "float", "bool", "bytes",
        "list", "dict", "set", "tuple", "range", "complex"
    )

    /**
     * Returns true if the given name (case-insensitive) is a Python builtin type name.
     */
    fun isBuiltin(name: String): Boolean = name.lowercase() in BUILTIN_NAMES
}
