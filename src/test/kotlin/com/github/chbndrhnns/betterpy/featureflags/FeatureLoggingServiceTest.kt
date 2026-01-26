package com.github.chbndrhnns.betterpy.featureflags

import fixtures.TestBase

class FeatureLoggingServiceTest : TestBase() {

    fun testLoggingCategoriesSupportPackages() {
        val feature = FeatureRegistry.instance().getFeature("advanced-pytest-fixtures")
        assertNotNull("Feature 'advanced-pytest-fixtures' should exist", feature)
        feature!!

        val formatted = FeatureLoggingService().formatLoggingCategories(feature.loggingCategories)

        assertTrue(
            "Expected package logging category to be formatted with trace:separate",
            formatted.lines().contains("com.github.chbndrhnns.betterpy.features.pytest.fixture:trace:separate")
        )
    }
}
