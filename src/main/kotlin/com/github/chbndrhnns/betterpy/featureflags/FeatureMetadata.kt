package com.github.chbndrhnns.betterpy.featureflags

/**
 * Marks a feature's maturity level.
 */
enum class FeatureMaturity {
    /** Feature is stable and ready for production use */
    STABLE,
    /** Feature is in development, may change or be removed */
    INCUBATING,
    /** Feature is hidden from UI, only accessible via registry/config */
    HIDDEN,
    /** Feature is deprecated and will be removed */
    DEPRECATED
}

/**
 * Category for grouping features in settings UI.
 */
enum class FeatureCategory(val displayName: String) {
    TYPE_WRAPPING("Type Wrapping/Unwrapping"),
    ARGUMENTS("Parameter & Argument"),
    CODE_STRUCTURE("Code Structure"),
    ABSTRACT_METHODS("Abstract Methods"),
    PYTEST("Pytest"),
    INSPECTIONS("Inspections"),
    ACTIONS("Actions"),
    MCP_TOOLS("MCP Tools"),
    COMPLETION("Completion & References"),
    IMPORTS("Import & Structure"),
    NAVIGATION("Navigation"),
    FILTERS("Filters & Suppressors"),
    CONNEXION("Connexion"),
    DOCUMENTATION("Documentation"),
    OTHER("Other")
}

/**
 * Feature ID binding annotation for plugin features.
 * Applied to feature toggle properties in PluginSettingsState.State.
 *
 * Feature metadata is defined in YAML under src/main/resources/features.
 *
 * Example usage:
 * ```kotlin
 * @Feature("populate-arguments")
 * var enablePopulateArgumentsIntention: Boolean = true
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Feature(
    /** Unique feature identifier (e.g., "populate-arguments") */
    val id: String
)
