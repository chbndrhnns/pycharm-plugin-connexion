package com.github.chbndrhnns.intellijplatformplugincopy.settings

import fixtures.TestBase

/**
 * Tests for [FeatureRegistry] to verify annotation-based feature discovery and metadata access.
 */
class FeatureRegistryTest : TestBase() {

    private lateinit var registry: FeatureRegistry

    override fun setUp() {
        super.setUp()
        registry = FeatureRegistry.instance()
    }

    fun testAllFeaturesDiscovered() {
        val features = registry.getAllFeatures()
        // We have ~54 annotated features in PluginSettingsState.State
        assertTrue("Expected at least 50 features, got ${features.size}", features.size >= 50)
    }

    fun testFeatureHasRequiredMetadata() {
        val feature = registry.getFeature("populate-arguments")
        assertNotNull("Feature 'populate-arguments' should exist", feature)
        feature!!

        assertEquals("populate-arguments", feature.id)
        assertEquals("Populate arguments", feature.displayName)
        assertTrue("Description should not be empty", feature.description.isNotEmpty())
        assertEquals(FeatureCategory.ARGUMENTS, feature.category)
        assertEquals(FeatureMaturity.STABLE, feature.maturity)
        assertEquals("enablePopulateArgumentsIntention", feature.propertyName)
    }

    fun testIncubatingFeatureDetected() {
        val feature = registry.getFeature("callable-to-protocol")
        assertNotNull("Feature 'callable-to-protocol' should exist", feature)
        feature!!

        assertEquals(FeatureMaturity.INCUBATING, feature.maturity)
    }

    fun testGetFeaturesByCategory() {
        val typeWrappingFeatures = registry.getFeaturesByCategory(FeatureCategory.TYPE_WRAPPING)
        assertTrue("Should have type wrapping features", typeWrappingFeatures.isNotEmpty())

        typeWrappingFeatures.forEach { feature ->
            assertEquals(FeatureCategory.TYPE_WRAPPING, feature.category)
        }
    }

    fun testGetFeaturesByMaturity() {
        val incubatingFeatures = registry.getFeaturesByMaturity(FeatureMaturity.INCUBATING)
        // We have at least one incubating feature (callable-to-protocol)
        assertTrue("Should have at least one incubating feature", incubatingFeatures.isNotEmpty())

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
        val feature = registry.getFeature("populate-arguments")
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
        assertTrue(registry.isFeatureEnabled("populate-arguments"))

        // Non-existent feature should return false
        assertFalse(registry.isFeatureEnabled("non-existent-feature"))
    }

    fun testSetFeatureEnabledById() {
        registry.setFeatureEnabled("populate-arguments", false)
        assertFalse(registry.isFeatureEnabled("populate-arguments"))

        registry.setFeatureEnabled("populate-arguments", true)
        assertTrue(registry.isFeatureEnabled("populate-arguments"))
    }

    fun testGetFeaturesByCategories() {
        val byCategory = registry.getFeaturesByCategories()

        assertTrue("Should have multiple categories", byCategory.keys.size > 5)

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
            categoriesWithFeatures.size >= 8
        )
    }
}
