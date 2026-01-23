package com.github.chbndrhnns.betterpy.features.intentions.customtype

/**
 * Defines the different kinds of custom types that can be generated
 * when introducing a custom type from a stdlib builtin.
 */
enum class CustomTypeKind(val displayName: String) {
    /**
     * Simple subclass of the builtin type.
     * Example: `class ProductId(int): pass`
     */
    SUBCLASS("Subclass"),

    /**
     * NewType alias for static type checking only.
     * Example: `ProductId = NewType("ProductId", int)`
     */
    NEWTYPE("NewType"),

    /**
     * Frozen dataclass with a `.value` field.
     * Example:
     * ```
     * @dataclass(frozen=True)
     * class ProductId:
     *     value: int
     * ```
     */
    FROZEN_DATACLASS("Frozen Dataclass"),

    /**
     * Pydantic model with a `.value` field and frozen config.
     * Example:
     * ```
     * class ProductId(BaseModel):
     *     value: int
     *     model_config = ConfigDict(frozen=True)
     * ```
     */
    PYDANTIC_VALUE_OBJECT("Pydantic Value Object");

    companion object {
        val DEFAULT = SUBCLASS
    }
}
