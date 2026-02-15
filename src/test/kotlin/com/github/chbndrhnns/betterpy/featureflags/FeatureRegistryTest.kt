package com.github.chbndrhnns.betterpy.featureflags

import com.intellij.openapi.application.ApplicationInfo
import fixtures.TestBase

/**
 * Tests for [FeatureRegistry] to verify feature discovery and metadata access.
 */
class FeatureRegistryTest : TestBase() {

    private lateinit var registry: FeatureRegistry

    override fun setUp() {
        super.setUp()
        registry = FeatureRegistry.instance()
    }

    fun testAllFeaturesDiscovered() {
        val features = registry.getAllFeatures()
        assertTrue("Expected at least 5 features, got ${features.size}", features.size >= 5)
    }

    fun testFeatureHasRequiredMetadata() {
        val feature = registry.getFeature("parameter-object-refactoring")
        assertNotNull("Feature 'parameter-object-refactoring' should exist", feature)
        feature!!

        assertEquals("parameter-object-refactoring", feature.id)
        assertEquals("Parameter object refactoring", feature.displayName)
        assertTrue("Description should not be empty", feature.description.isNotEmpty())
        assertEquals(FeatureCategory.ACTIONS, feature.category)
        assertEquals(FeatureMaturity.STABLE, feature.maturity)
        assertEquals("enableParameterObjectRefactoring", feature.propertyName)
    }

    fun testStableFeatureDetected() {
        val feature = registry.getFeature("make-parameter-optional")
        assertNotNull("Feature 'make-parameter-optional' should exist", feature)
        feature!!

        assertEquals(FeatureMaturity.STABLE, feature.maturity)
    }

    fun testGetFeaturesByCategory() {
        val actionFeatures = registry.getFeaturesByCategory(FeatureCategory.ACTIONS)
        assertTrue("Should have action features", actionFeatures.isNotEmpty())

        actionFeatures.forEach { feature ->
            assertEquals(FeatureCategory.ACTIONS, feature.category)
        }
    }

    fun testGetFeaturesByMaturity() {
        val incubatingFeatures = registry.getFeaturesByMaturity(FeatureMaturity.INCUBATING)
        incubatingFeatures.forEach { feature ->
            assertEquals(FeatureMaturity.INCUBATING, feature.maturity)
        }
    }

    fun testGetVisibleFeaturesExcludesHidden() {
        val visibleFeatures = registry.getVisibleFeatures()
        val hiddenFeatures = registry.getHiddenFeatures()

        visibleFeatures.forEach { feature ->
            assertFalse(
                "Visible features should not include hidden ones: ${feature.id}",
                feature.maturity == FeatureMaturity.HIDDEN
            )
        }

        hiddenFeatures.forEach { feature ->
            assertEquals(FeatureMaturity.HIDDEN, feature.maturity)
        }
    }

    fun testFeatureEnabledState() {
        val feature = registry.getFeature("make-parameter-optional")
        assertNotNull(feature)
        feature!!

        // Default should be enabled
        assertTrue("Feature should be enabled by default", feature.isEnabled())

        // Toggle off
        feature.setEnabled(false)
        assertFalse("Feature should be disabled after toggle", feature.isEnabled())

        // Toggle back on
        feature.setEnabled(true)
        assertTrue("Feature should be enabled after toggle back", feature.isEnabled())
    }

    fun testIsFeatureEnabledById() {
        // Default should be enabled
        assertTrue(registry.isFeatureEnabled("make-parameter-optional"))

        // Non-existent feature should return false
        assertFalse(registry.isFeatureEnabled("non-existent-feature"))
    }

    fun testSetFeatureEnabledById() {
        registry.setFeatureEnabled("make-parameter-optional", false)
        assertFalse(registry.isFeatureEnabled("make-parameter-optional"))

        registry.setFeatureEnabled("make-parameter-optional", true)
        assertTrue(registry.isFeatureEnabled("make-parameter-optional"))
    }

    fun testGetFeaturesByCategories() {
        val byCategory = registry.getFeaturesByCategories()

        assertTrue("Should have multiple categories", byCategory.keys.size >= 2)

        byCategory.forEach { (category, features) ->
            features.forEach { feature ->
                assertEquals(
                    "Feature ${feature.id} should be in category $category",
                    category,
                    feature.category
                )
            }
        }
    }

    fun testGetVisibleFeaturesByCategories() {
        val byCategory = registry.getVisibleFeaturesByCategories()

        byCategory.values.flatten().forEach { feature ->
            assertFalse(
                "Should not include hidden features: ${feature.id}",
                feature.maturity == FeatureMaturity.HIDDEN
            )
        }
    }

    fun testYouTrackUrlGeneration() {
        val url = FeatureRegistry.FeatureInfo.getYouTrackUrl("PY-12345")
        assertEquals("https://youtrack.jetbrains.com/issue/PY-12345", url)
    }

    fun testGetIncubatingFeatures() {
        val incubating = registry.getIncubatingFeatures()
        incubating.forEach { feature ->
            assertEquals(FeatureMaturity.INCUBATING, feature.maturity)
        }
    }

    fun testGetDeprecatedFeatures() {
        val deprecated = registry.getDeprecatedFeatures()
        deprecated.forEach { feature ->
            assertEquals(FeatureMaturity.DEPRECATED, feature.maturity)
        }
    }

    fun testGetEnabledIncubatingFeatures() {
        val enabledIncubating = registry.getEnabledIncubatingFeatures()
        enabledIncubating.forEach { feature ->
            assertEquals(FeatureMaturity.INCUBATING, feature.maturity)
            assertTrue("Feature should be enabled", feature.isEnabled())
        }
    }

    fun testBundledInFeatureIsUnavailableAndHidden() {
        val currentBuild = ApplicationInfo.getInstance().build.asString()
        val id = "test-bundled-in-feature"
        var enabled = true

        val feature = FeatureRegistry.FeatureInfo(
            id = id,
            displayName = "Test Bundled Feature",
            description = "A fake bundled feature for testing",
            maturity = FeatureMaturity.STABLE,
            category = FeatureCategory.OTHER,
            youtrackIssues = emptyList(),
            loggingCategories = emptyList(),
            since = "",
            removeIn = "",
            minBuild = "",
            bundledIn = currentBuild,
            propertyName = id,
            getter = { enabled },
            setter = { enabled = it }
        )

        registry.registerAdditionalFeature(feature)
        try {
            assertFalse("Feature should be unavailable for current build", feature.isAvailable())
            assertFalse("Feature should not be enabled when unavailable", feature.isEnabled())
            assertFalse("Registry should report unavailable feature as disabled", registry.isFeatureEnabled(id))
            assertFalse(
                "Unavailable features should be excluded from visible list",
                registry.getVisibleFeatures().any { it.id == id }
            )
        } finally {
            registry.unregisterAdditionalFeature(id)
        }
    }

    fun testDisableUnavailableFeaturesForcesStateOff() {
        val currentBuild = ApplicationInfo.getInstance().build.asString()
        val id = "test-bundled-in-feature-disable"
        var enabled = true

        val feature = FeatureRegistry.FeatureInfo(
            id = id,
            displayName = "Test Bundled Feature Disable",
            description = "A fake bundled feature for testing disable",
            maturity = FeatureMaturity.STABLE,
            category = FeatureCategory.OTHER,
            youtrackIssues = emptyList(),
            loggingCategories = emptyList(),
            since = "",
            removeIn = "",
            minBuild = "",
            bundledIn = currentBuild,
            propertyName = id,
            getter = { enabled },
            setter = { enabled = it }
        )

        registry.registerAdditionalFeature(feature)
        try {
            assertTrue("Test setup should start enabled", enabled)
            registry.disableUnavailableFeatures()
            assertFalse("disableUnavailableFeatures should turn off state", enabled)
        } finally {
            registry.unregisterAdditionalFeature(id)
        }
    }

    fun testMinBuildUnavailableFeatureIsHidden() {
        val id = "test-min-build-feature"
        var enabled = true

        val feature = FeatureRegistry.FeatureInfo(
            id = id,
            displayName = "Test Min Build Feature",
            description = "A fake min build feature for testing",
            maturity = FeatureMaturity.STABLE,
            category = FeatureCategory.OTHER,
            youtrackIssues = emptyList(),
            loggingCategories = emptyList(),
            since = "",
            removeIn = "",
            minBuild = "999.99999",
            bundledIn = "",
            propertyName = id,
            getter = { enabled },
            setter = { enabled = it }
        )

        registry.registerAdditionalFeature(feature)
        try {
            assertFalse("Feature should be unavailable for current build", feature.isAvailable())
            assertFalse("Feature should not be enabled when unavailable", feature.isEnabled())
            assertNotNull("Unavailable feature should have a reason", feature.unavailabilityReason())
            assertTrue(
                "Reason should mention minimum build",
                feature.unavailabilityReason()!!.contains("Requires IDE build >=")
            )
            assertFalse(
                "Unavailable features should be excluded from visible list",
                registry.getVisibleFeatures().any { it.id == id }
            )
        } finally {
            registry.unregisterAdditionalFeature(id)
        }
    }

    fun testFeatureNotFound() {
        val feature = registry.getFeature("non-existent-feature-id")
        assertNull("Non-existent feature should return null", feature)
    }

    fun testAllCategoriesHaveFeatures() {
        // Most categories should have at least one feature
        val categoriesWithFeatures = registry.getFeaturesByCategories().keys
        assertTrue(
            "Should have features in multiple categories",
            categoriesWithFeatures.containsAll(setOf(FeatureCategory.ARGUMENTS, FeatureCategory.ACTIONS))
        )
    }
}
