package com.github.chbndrhnns.intellijplatformplugincopy.settings

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
    COMPLETION("Completion & References"),
    IMPORTS("Import & Structure"),
    NAVIGATION("Navigation"),
    FILTERS("Filters & Suppressors"),
    CONNEXION("Connexion"),
    OTHER("Other")
}

/**
 * Metadata annotation for plugin features.
 * Applied to feature toggle properties in PluginSettingsState.State.
 *
 * Example usage:
 * ```kotlin
 * @Feature(
 *     id = "populate-arguments",
 *     displayName = "Populate arguments",
 *     description = "Automatically fills in function call arguments based on signature",
 *     maturity = FeatureMaturity.STABLE,
 *     category = FeatureCategory.ARGUMENTS,
 *     youtrackIssues = ["PY-12345"],
 *     since = "1.0.0"
 * )
 * var enablePopulateArgumentsIntention: Boolean = true
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Feature(
    /** Unique feature identifier (e.g., "populate-arguments") */
    val id: String,

    /** Human-readable feature name */
    val displayName: String,

    /** Brief description of what the feature does */
    val description: String = "",

    /** Feature maturity level */
    val maturity: FeatureMaturity = FeatureMaturity.STABLE,

    /** Category for grouping in settings UI */
    val category: FeatureCategory = FeatureCategory.OTHER,

    /** Related YouTrack issue IDs (e.g., ["PY-12345", "PY-67890"]) */
    val youtrackIssues: Array<String> = [],

    /**
     * Debug logging categories associated with this feature.
     * If provided, a context menu action will be available to toggle debug logging for these categories.
     * Example: ["com.example.MyClass", "com.example.mypackage"]
     */
    val loggingCategories: Array<String> = [],

    /** Version when feature was introduced */
    val since: String = "",

    /** Version when feature will be removed (for deprecated features) */
    val removeIn: String = ""
)
