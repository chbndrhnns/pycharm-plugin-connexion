package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

/**
 * Defines the mode for populating arguments.
 */
enum class PopulateMode {
    /** Populate all missing arguments (including those with default values). */
    ALL,
    /** Populate only required arguments (those without default values). */
    REQUIRED_ONLY
}

/**
 * Configuration options for the populate arguments intention.
 *
 * @property mode Whether to populate all arguments or only required ones.
 * @property recursive Whether to recursively expand nested dataclasses/Pydantic models.
 * @property useLocalScope Whether to use local variables if they match argument names.
 */
data class PopulateOptions(
    val mode: PopulateMode = PopulateMode.ALL,
    val recursive: Boolean = false,
    val useLocalScope: Boolean = false
) {
    /**
     * Returns a human-readable label for display in the popup chooser.
     */
    fun label(): String = when {
        useLocalScope -> "Populate arguments (from locals)"
        mode == PopulateMode.ALL && !recursive -> "All arguments"
        mode == PopulateMode.ALL && recursive -> "All arguments (recursive)"
        mode == PopulateMode.REQUIRED_ONLY && !recursive -> "Required arguments only"
        mode == PopulateMode.REQUIRED_ONLY && recursive -> "Required arguments only (recursive)"
        else -> "Populate arguments"
    }

    companion object {
        /** All possible option combinations for the popup. */
        val ALL_OPTIONS = listOf(
            PopulateOptions(PopulateMode.ALL, recursive = false),
            PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = false),
            PopulateOptions(PopulateMode.ALL, recursive = true),
            PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = true),
            PopulateOptions(PopulateMode.ALL, recursive = false, useLocalScope = true)
        )

        /** Options without recursive variants (when recursive is not applicable). */
        val NON_RECURSIVE_OPTIONS = listOf(
            PopulateOptions(PopulateMode.ALL, recursive = false),
            PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = false),
            PopulateOptions(PopulateMode.ALL, recursive = false, useLocalScope = true)
        )
    }
}
