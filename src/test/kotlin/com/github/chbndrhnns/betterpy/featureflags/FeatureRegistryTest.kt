package com.github.chbndrhnns.betterpy.featureflags

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
