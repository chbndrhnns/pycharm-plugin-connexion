package com.github.chbndrhnns.intellijplatformplugincopy.featureflags

/**
 * A snapshot of feature states that can be used for copy-on-edit state management.
 * This allows UI changes to be made to a temporary copy, then applied all at once.
 *
 * This implements the standard IntelliJ settings pattern:
 * - UI binds to snapshot (temporary state)
 * - apply() copies snapshot → persistent state
 * - reset() copies persistent state → snapshot
 * - isModified checks if snapshot differs from persistent state
 */
class FeatureStateSnapshot private constructor(
    private val originalStates: Map<String, Boolean>
) {
    private val modifiedStates = originalStates.toMutableMap()

    /**
     * Gets the current state of a feature from the snapshot.
     */
    fun isEnabled(featureId: String): Boolean =
        modifiedStates[featureId] ?: false

    /**
     * Sets the state of a feature in the snapshot (does not affect persistent state).
     */
    fun setEnabled(featureId: String, enabled: Boolean) {
        modifiedStates[featureId] = enabled
    }

    /**
     * Checks if any feature state has been modified from the original.
     */
    fun isModified(): Boolean =
        modifiedStates != originalStates

    /**
     * Applies all changes from the snapshot to the persistent state.
     */
    fun applyTo(registry: FeatureRegistry) {
        modifiedStates.forEach { (id, enabled) ->
            registry.setFeatureEnabled(id, enabled)
        }
    }

    /**
     * Resets all changes, restoring the snapshot to the original state.
     */
    fun reset() {
        modifiedStates.clear()
        modifiedStates.putAll(originalStates)
    }

    /**
     * Creates a new snapshot with updated original state (used after apply).
     */
    fun withNewBaseline(): FeatureStateSnapshot {
        return FeatureStateSnapshot(modifiedStates.toMap())
    }

    companion object {
        /**
         * Creates a snapshot from the current persistent state.
         */
        fun fromRegistry(registry: FeatureRegistry): FeatureStateSnapshot {
            val states = registry.getAllFeatures().associate { feature ->
                feature.id to feature.isEnabled()
            }
            return FeatureStateSnapshot(states)
        }

        /**
         * Creates a snapshot from a specific set of feature IDs.
         * Useful when a configurable only shows a subset of features.
         */
        fun fromFeatures(registry: FeatureRegistry, featureIds: List<String>): FeatureStateSnapshot {
            val states = featureIds.mapNotNull { id ->
                registry.getFeature(id)?.let { feature ->
                    feature.id to feature.isEnabled()
                }
            }.toMap()
            return FeatureStateSnapshot(states)
        }
    }
}
