package com.github.chbndrhnns.betterpy.featureflags

import fixtures.TestBase

/**
 * Tests that settings changes are properly persisted across configurable instances.
 * This simulates the full user workflow: open settings, make changes, apply, close, reopen.
 */
class PersistenceTest : TestBase() {

    fun testSettingsPersistAcrossConfigurableInstances() {
        val registry = FeatureRegistry.instance()
        val feature = registry.getFeature("populate-arguments")!!

        // Save original state
        val originalState = feature.isEnabled()

        // Create first configurable instance (simulates opening settings)
        val configurable1 = PluginSettingsConfigurable()
        configurable1.createComponent()

        // Snapshot should capture original state
        assertFalse("Should not be modified initially", configurable1.isModified)

        // Get the current persistent value
        val stateBefore = feature.isEnabled()

        // Create a snapshot and modify it
        val snapshot = FeatureStateSnapshot.fromRegistry(registry)
        val newValue = !stateBefore
        snapshot.setEnabled(feature.id, newValue)

        // Apply snapshot to persistent state
        snapshot.applyTo(registry)

        // Verify persistent state changed
        assertEquals("Persistent state should be updated", newValue, feature.isEnabled())

        // Simulate closing and reopening settings by creating new configurable
        val configurable2 = PluginSettingsConfigurable()
        configurable2.createComponent()

        // New configurable should see the persisted change
        val feature2 = FeatureRegistry.instance().getFeature("populate-arguments")!!
        assertEquals("New configurable should see persisted state", newValue, feature2.isEnabled())

        // Restore original state
        feature.setEnabled(originalState)
    }

    fun testMultipleFeaturesPersist() {
        val registry = FeatureRegistry.instance()
        val feature1 = registry.getFeature("populate-arguments")!!
        val feature2 = registry.getFeature("make-parameter-optional")!!

        val original1 = feature1.isEnabled()
        val original2 = feature2.isEnabled()

        // Change both features
        val snapshot = FeatureStateSnapshot.fromRegistry(registry)
        snapshot.setEnabled(feature1.id, !original1)
        snapshot.setEnabled(feature2.id, !original2)
        snapshot.applyTo(registry)

        // Verify both persisted
        assertEquals("Feature 1 should persist", !original1, feature1.isEnabled())
        assertEquals("Feature 2 should persist", !original2, feature2.isEnabled())

        // Create new registry instance and verify persistence
        val newRegistry = FeatureRegistry.instance()
        val newFeature1 = newRegistry.getFeature("populate-arguments")!!
        val newFeature2 = newRegistry.getFeature("make-parameter-optional")!!

        assertEquals("Feature 1 should persist across instances", !original1, newFeature1.isEnabled())
        assertEquals("Feature 2 should persist across instances", !original2, newFeature2.isEnabled())

        // Restore original state
        feature1.setEnabled(original1)
        feature2.setEnabled(original2)
    }

    fun testApplyPersistsChanges() {
        val registry = FeatureRegistry.instance()
        val feature = registry.getFeature("wrap-with-expected-type")!!
        val originalState = feature.isEnabled()
        val newValue = !originalState

        // Simulate user workflow: change via snapshot and apply
        val snapshot = FeatureStateSnapshot.fromRegistry(registry)
        snapshot.setEnabled(feature.id, newValue)
        snapshot.applyTo(registry)

        // Verify change was applied to persistent state
        assertEquals("Change should be applied to persistent state", newValue, feature.isEnabled())

        // Simulate closing and reopening settings
        val newRegistry = FeatureRegistry.instance()
        val newFeature = newRegistry.getFeature("wrap-with-expected-type")!!
        assertEquals("Changes should persist across registry access", newValue, newFeature.isEnabled())

        // Restore original state
        feature.setEnabled(originalState)
    }
}
