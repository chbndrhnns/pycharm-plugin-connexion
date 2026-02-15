package com.github.chbndrhnns.betterpy.featureflags

import com.github.chbndrhnns.betterpy.featureflags.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel

class PluginSettingsConfigurable : BoundConfigurable("BetterPy"), SearchableConfigurable {
    private val registry = FeatureRegistry.instance()
    private lateinit var stateSnapshot: FeatureStateSnapshot

    override fun getId(): String = "com.github.chbndrhnns.betterpy.featureflags"

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        return createFilterableFeaturePanel { onFilterRefreshRequested ->
            val rows = mutableListOf<RowMetadata>()
            val featuresByCategory = registry.getVisibleFeaturesByCategories()

            FeatureCategory.entries.forEach { category ->
                if (category == FeatureCategory.PYTEST) {
                    return@forEach
                }
                val features = featuresByCategory[category] ?: return@forEach
                if (features.isEmpty()) return@forEach

                group(category.displayName) {
                    // Filter out parameter object features - they're in a separate configurable
                    val filteredFeatures = if (category == FeatureCategory.ACTIONS) {
                        features.filter { !it.id.startsWith("parameter-object-") }
                    } else features

                    filteredFeatures.forEach { feature ->
                        val row = featureRow(
                            feature,
                            getter = { stateSnapshot.isEnabled(feature.id) },
                            setter = { value -> stateSnapshot.setEnabled(feature.id, value) },
                            onToggle = onFilterRefreshRequested
                        )
                        rows.add(row)
                    }
                }
            }

            val unavailableFeatures = registry.getUnavailableFeatures()
                .filter { it.maturity != FeatureMaturity.HIDDEN }
                .sortedBy { it.displayName }
            if (unavailableFeatures.isNotEmpty()) {
                group("Unavailable features") {
                    row {
                        label("These features are disabled for this IDE build.")
                    }
                    unavailableFeatures.forEach { feature ->
                        val reason = feature.unavailabilityReason()
                            ?: "Unavailable for the current IDE build"
                        row {
                            label(feature.displayName)
                                .comment(reason)
                        }
                    }
                }
            }

            rows
        }.asDialogPanel()
    }

    override fun isModified(): Boolean {
        // Check both snapshot's internal modification tracking and parent's UI tracking
        val snapshotModified = ::stateSnapshot.isInitialized && stateSnapshot.isModified()
        val boundModified = super<BoundConfigurable>.isModified()

        return snapshotModified || boundModified
    }

    override fun apply() {
        super<BoundConfigurable>.apply()
        if (::stateSnapshot.isInitialized) {
            stateSnapshot.applyTo(registry)
            stateSnapshot = stateSnapshot.withNewBaseline()
        }
    }

    override fun reset() {
        if (::stateSnapshot.isInitialized) {
            stateSnapshot.reset()
            super<BoundConfigurable>.reset()
        }
    }
}
