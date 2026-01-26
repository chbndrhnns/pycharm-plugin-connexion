package com.github.chbndrhnns.betterpy.featureflags

import com.github.chbndrhnns.betterpy.featureflags.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel

class PytestConfigurable : BoundConfigurable("Pytest"), SearchableConfigurable {
    private val registry = FeatureRegistry.instance()
    private lateinit var stateSnapshot: FeatureStateSnapshot

    override fun getId(): String = "com.github.chbndrhnns.betterpy.featureflags.pytest"

    override fun createPanel(): DialogPanel {
        stateSnapshot = FeatureStateSnapshot.fromRegistry(registry)

        return createFilterableFeaturePanel { _ ->
            val rows = mutableListOf<RowMetadata>()
            val features = registry.getVisibleFeaturesByCategories()[FeatureCategory.PYTEST].orEmpty()

            if (features.isNotEmpty()) {
                group(FeatureCategory.PYTEST.displayName) {
                    features.forEach { feature ->
                        val row = featureRow(
                            feature,
                            getter = { stateSnapshot.isEnabled(feature.id) },
                            setter = { value -> stateSnapshot.setEnabled(feature.id, value) }
                        )
                        rows.add(row)
                    }
                }
            }

            rows
        }.asDialogPanel()
    }

    override fun isModified(): Boolean {
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
