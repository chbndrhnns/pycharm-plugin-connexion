package com.github.chbndrhnns.intellijplatformplugincopy.settings

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
        val state = PluginSettingsState.instance().state
        val result = mutableMapOf<String, FeatureInfo>()

        PluginSettingsState.State::class.memberProperties.forEach { prop ->
            val annotation = prop.findAnnotation<Feature>()
                ?: prop.javaField?.getAnnotation(Feature::class.java)

            if (annotation != null && prop is KMutableProperty1<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val mutableProp = prop as KMutableProperty1<PluginSettingsState.State, Boolean>

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
                    getter = { mutableProp.get(state) },
                    setter = { value -> mutableProp.set(state, value) }
                )
            }
        }

        return result
    }

    companion object {
        @JvmStatic
        fun instance(): FeatureRegistry = service()
    }
}
