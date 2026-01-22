package com.github.chbndrhnns.intellijplatformplugincopy.featureflags

import fixtures.TestBase

/**
 * Tests the copy-on-edit state management pattern for configurables.
 * Tests the FeatureStateSnapshot class and verifies configurables can be instantiated.
 */
class SnapshotStateManagementTest : TestBase() {

    fun testSnapshotCreation() {
        val registry = FeatureRegistry.instance()
        val snapshot = FeatureStateSnapshot.fromRegistry(registry)

        // Snapshot should have feature states
        val feature = registry.getFeature("populate-arguments")!!
        val snapshotValue = snapshot.isEnabled(feature.id)
        val persistentValue = feature.isEnabled()

        assertEquals("Snapshot should match persistent state initially", persistentValue, snapshotValue)
    }

    fun testSnapshotModification() {
        val registry = FeatureRegistry.instance()
        val snapshot = FeatureStateSnapshot.fromRegistry(registry)
        val feature = registry.getFeature("populate-arguments")!!

        val originalValue = snapshot.isEnabled(feature.id)
        assertFalse("Should not be modified initially", snapshot.isModified())

        // Modify snapshot (not persistent state)
        snapshot.setEnabled(feature.id, !originalValue)
        assertTrue("Should be modified after change", snapshot.isModified())

        // Persistent state should be unchanged
        assertEquals("Persistent state should be unchanged", originalValue, feature.isEnabled())
    }

    fun testSnapshotApply() {
        val registry = FeatureRegistry.instance()
        val feature = registry.getFeature("populate-arguments")!!
        val originalValue = feature.isEnabled()

        val snapshot = FeatureStateSnapshot.fromRegistry(registry)
        val newValue = !originalValue

        // Modify snapshot
        snapshot.setEnabled(feature.id, newValue)

        // Apply to persistent state
        snapshot.applyTo(registry)

        // Persistent state should now match snapshot
        assertEquals("Persistent state should be updated", newValue, feature.isEnabled())

        // Restore original
        feature.setEnabled(originalValue)
    }

    fun testSnapshotReset() {
        val registry = FeatureRegistry.instance()
        val snapshot = FeatureStateSnapshot.fromRegistry(registry)
        val feature = registry.getFeature("populate-arguments")!!

        val originalValue = snapshot.isEnabled(feature.id)

        // Modify snapshot
        snapshot.setEnabled(feature.id, !originalValue)
        assertTrue("Should be modified", snapshot.isModified())

        // Reset
        snapshot.reset()
        assertFalse("Should not be modified after reset", snapshot.isModified())
        assertEquals("Should match original after reset", originalValue, snapshot.isEnabled(feature.id))
    }

    fun testSnapshotWithNewBaseline() {
        val registry = FeatureRegistry.instance()
        val snapshot = FeatureStateSnapshot.fromRegistry(registry)
        val feature = registry.getFeature("populate-arguments")!!

        val originalValue = snapshot.isEnabled(feature.id)
        val newValue = !originalValue

        // Modify and apply
        snapshot.setEnabled(feature.id, newValue)
        snapshot.applyTo(registry)

        // Create new baseline
        val newSnapshot = snapshot.withNewBaseline()
        assertFalse("New snapshot should not be modified", newSnapshot.isModified())
        assertEquals("New snapshot should have updated baseline", newValue, newSnapshot.isEnabled(feature.id))

        // Restore original
        feature.setEnabled(originalValue)
    }

    fun testConfigurableInstantiation() {
        // Just verify all configurables can be created without errors
        val pluginSettings = PluginSettingsConfigurable()
        val parameterObjectSettings = ParameterObjectConfigurable()

        // Create panels to ensure snapshot initialization works
        assertNotNull(pluginSettings.createComponent())
        assertNotNull(parameterObjectSettings.createComponent())
    }
}
