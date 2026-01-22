package com.github.chbndrhnns.intellijplatformplugincopy.featureflags

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Service that provides access to feature metadata and manages feature state.
 * Features are discovered via reflection from [PluginSettingsState.State] properties
 * annotated with [Feature].
 */
@Service
class FeatureRegistry {

    /**
     * Information about a single feature, combining annotation metadata with runtime accessors.
     */
    data class FeatureInfo(
        val id: String,
        val displayName: String,
        val description: String,
        val maturity: FeatureMaturity,
        val category: FeatureCategory,
        val youtrackIssues: List<String>,
        val loggingCategories: List<String>,
        val since: String,
        val removeIn: String,
        val propertyName: String,
        private val getter: () -> Boolean,
        private val setter: (Boolean) -> Unit
    ) {
        /** Returns whether this feature is currently enabled. */
        fun isEnabled(): Boolean = getter()

        /** Sets whether this feature is enabled. */
        fun setEnabled(value: Boolean) = setter(value)

        /** Returns YouTrack URLs for all related issues. */
        fun getYouTrackUrls(): List<String> = youtrackIssues.map { getYouTrackUrl(it) }

        companion object {
            private const val YOUTRACK_BASE_URL = "https://youtrack.jetbrains.com/issue/"

            fun getYouTrackUrl(issueId: String): String = "$YOUTRACK_BASE_URL$issueId"
        }
    }

    private val features: Map<String, FeatureInfo> by lazy { buildFeatureMap() }

    /** Returns all registered features. */
    fun getAllFeatures(): List<FeatureInfo> = features.values.toList()

    /** Returns a feature by its ID, or null if not found. */
    fun getFeature(id: String): FeatureInfo? = features[id]

    /** Returns all features in the specified category. */
    fun getFeaturesByCategory(category: FeatureCategory): List<FeatureInfo> =
        features.values.filter { it.category == category }

    /** Returns all features with the specified maturity level. */
    fun getFeaturesByMaturity(maturity: FeatureMaturity): List<FeatureInfo> =
        features.values.filter { it.maturity == maturity }

    /** Returns all incubating features. */
    fun getIncubatingFeatures(): List<FeatureInfo> =
        getFeaturesByMaturity(FeatureMaturity.INCUBATING)

    /** Returns all deprecated features. */
    fun getDeprecatedFeatures(): List<FeatureInfo> =
        getFeaturesByMaturity(FeatureMaturity.DEPRECATED)

    /** Returns all features that should be visible in the settings UI (excludes HIDDEN). */
    fun getVisibleFeatures(): List<FeatureInfo> =
        features.values.filter { it.maturity != FeatureMaturity.HIDDEN }

    /** Returns all hidden features. */
    fun getHiddenFeatures(): List<FeatureInfo> =
        getFeaturesByMaturity(FeatureMaturity.HIDDEN)

    /** Returns whether a feature is enabled by its ID. Returns false if feature not found. */
    fun isFeatureEnabled(id: String): Boolean =
        features[id]?.isEnabled() ?: false

    /** Sets whether a feature is enabled by its ID. Does nothing if feature not found. */
    fun setFeatureEnabled(id: String, enabled: Boolean) {
        features[id]?.setEnabled(enabled)
    }

    /** Returns all enabled incubating features. */
    fun getEnabledIncubatingFeatures(): List<FeatureInfo> =
        getIncubatingFeatures().filter { it.isEnabled() }

    /** Returns all enabled deprecated features. */
    fun getEnabledDeprecatedFeatures(): List<FeatureInfo> =
        getDeprecatedFeatures().filter { it.isEnabled() }

    /** Returns features grouped by category. */
    fun getFeaturesByCategories(): Map<FeatureCategory, List<FeatureInfo>> =
        features.values.groupBy { it.category }

    /** Returns visible features grouped by category. */
    fun getVisibleFeaturesByCategories(): Map<FeatureCategory, List<FeatureInfo>> =
        getVisibleFeatures().groupBy { it.category }

    /** Returns all unique YouTrack issue IDs referenced by features. */
    fun getAllYouTrackIssues(): Set<String> =
        features.values.flatMap { it.youtrackIssues }.toSet()

    /** Returns features that reference a specific YouTrack issue. */
    fun getFeaturesByYouTrackIssue(issueId: String): List<FeatureInfo> =
        features.values.filter { issueId in it.youtrackIssues }

    @Suppress("UNCHECKED_CAST")
    private fun buildFeatureMap(): Map<String, FeatureInfo> {
        val result = mutableMapOf<String, FeatureInfo>()
        val visited = mutableSetOf<kotlin.reflect.KClass<*>>()

        // Scan top-level properties in PluginSettingsState.State
        scanPropertiesForFeatures(
            klass = PluginSettingsState.State::class,
            instanceGetter = { PluginSettingsState.instance().state },
            result = result,
            visited = visited
        )

        return result
    }

    /**
     * Recursively scans properties of a class for @Feature annotations.
     * Supports both direct Boolean properties and nested settings objects.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> scanPropertiesForFeatures(
        klass: kotlin.reflect.KClass<T>,
        instanceGetter: () -> T,
        result: MutableMap<String, FeatureInfo>,
        visited: MutableSet<kotlin.reflect.KClass<*>>
    ) {
        // Prevent infinite recursion by tracking visited classes
        if (!visited.add(klass)) {
            return
        }

        klass.memberProperties.forEach { prop ->
            val annotation = prop.findAnnotation<Feature>()
                ?: prop.javaField?.getAnnotation(Feature::class.java)

            if (annotation != null && prop is KMutableProperty1<*, *>) {
                // This is a @Feature-annotated Boolean property
                val mutableProp = prop as KMutableProperty1<T, Boolean>

                result[annotation.id] = FeatureInfo(
                    id = annotation.id,
                    displayName = annotation.displayName,
                    description = annotation.description,
                    maturity = annotation.maturity,
                    category = annotation.category,
                    youtrackIssues = annotation.youtrackIssues.toList(),
                    loggingCategories = annotation.loggingCategories.toList(),
                    since = annotation.since,
                    removeIn = annotation.removeIn,
                    propertyName = prop.name,
                    getter = {
                        val instance = instanceGetter()
                        mutableProp.get(instance)
                    },
                    setter = { value ->
                        val instance = instanceGetter()
                        mutableProp.set(instance, value)
                    }
                )
            } else if (prop is KMutableProperty1<*, *> && prop.returnType.classifier is kotlin.reflect.KClass<*>) {
                // Check if this is a nested settings object (data class with @Feature-annotated properties)
                val nestedClass = prop.returnType.classifier as? kotlin.reflect.KClass<*>
                if (nestedClass != null && hasFeatureAnnotatedProperties(nestedClass)) {
                    // Recursively scan the nested object
                    val nestedProp = prop as KMutableProperty1<T, Any>
                    scanPropertiesForFeatures(
                        klass = nestedClass as kotlin.reflect.KClass<Any>,
                        instanceGetter = {
                            val parentInstance = instanceGetter()
                            nestedProp.get(parentInstance)
                        },
                        result = result,
                        visited = visited
                    )
                }
            }
        }
    }

    /**
     * Checks if a class has any properties annotated with @Feature.
     */
    private fun hasFeatureAnnotatedProperties(klass: kotlin.reflect.KClass<*>): Boolean {
        return klass.memberProperties.any { prop ->
            prop.findAnnotation<Feature>() != null || prop.javaField?.getAnnotation(Feature::class.java) != null
        }
    }

    companion object {
        @JvmStatic
        fun instance(): FeatureRegistry = service()
    }
}
