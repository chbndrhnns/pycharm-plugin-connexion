package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

/**
 * Represents the available base types for parameter objects.
 *
 * @property displayName The human-readable name shown in the UI
 */
enum class ParameterObjectBaseType(val displayName: String) {
    DATACLASS("dataclass"),
    NAMED_TUPLE("NamedTuple"),
    TYPED_DICT("TypedDict"),
    PYDANTIC_BASE_MODEL("pydantic.BaseModel");

    companion object {
        /**
         * Returns the default base type for parameter objects.
         */
        fun default(): ParameterObjectBaseType = DATACLASS

        /**
         * Finds a base type by its display name, or returns the default if not found.
         */
        fun fromDisplayName(displayName: String): ParameterObjectBaseType =
            entries.find { it.displayName == displayName } ?: default()
    }
}
