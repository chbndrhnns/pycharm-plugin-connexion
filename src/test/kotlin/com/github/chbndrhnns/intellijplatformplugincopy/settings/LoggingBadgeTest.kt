package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.ui.dsl.builder.panel
import fixtures.TestBase
import javax.swing.JComponent

/**
 * Tests for logging badge functionality in the settings UI.
 * Verifies that the logging badge appears immediately when logging is enabled
 * and disappears when logging is disabled, without requiring the settings to be reopened.
 */
class LoggingBadgeTest : TestBase() {

    private lateinit var registry: FeatureRegistry
    private lateinit var loggingService: FeatureLoggingService

    override fun setUp() {
        super.setUp()
        registry = FeatureRegistry.instance()
        loggingService = FeatureLoggingService.instance()
    }

    override fun tearDown() {
        // Clean up any logging state
        val feature = registry.getFeature("pytest-failed-line-inspection")
        if (feature != null && loggingService.isLoggingEnabled(feature)) {
            loggingService.disableLogging(feature)
        }
        super.tearDown()
    }

    fun testLoggingBadgeAppearsImmediately() {
        val feature = registry.getFeature("pytest-failed-line-inspection")
        assertNotNull("Feature 'pytest-failed-line-inspection' should exist", feature)
        feature!!

        // Ensure logging is initially disabled
        if (loggingService.isLoggingEnabled(feature)) {
            loggingService.disableLogging(feature)
        }
        assertFalse("Logging should be disabled initially", loggingService.isLoggingEnabled(feature))

        // Track whether the rebuild callback was invoked
        var rebuildCount = 0
        val onLoggingChanged: () -> Unit = { rebuildCount++ }

        // Build a panel with the feature row
        val panel = panel {
            featureRow(feature, { false }, {}, null, onLoggingChanged)
        }

        // Verify no logging badge exists initially
        val initialBadge = findLoggingBadge(panel)
        assertNull("Logging badge should not exist when logging is disabled", initialBadge)

        // Enable logging
        loggingService.enableLogging(feature)
        assertTrue("Logging should be enabled", loggingService.isLoggingEnabled(feature))

        // Rebuild the panel (simulating what happens in the real UI)
        val rebuiltPanel = panel {
            featureRow(feature, { false }, {}, null, onLoggingChanged)
        }

        // Verify logging badge now exists
        val badgeAfterEnable = findLoggingBadge(rebuiltPanel)
        assertNotNull("Logging badge should exist after enabling logging", badgeAfterEnable)

        // Verify the badge has the correct text
        val badgeLabel = findLabelInComponent(badgeAfterEnable!!, "Logging")
        assertNotNull("Badge should contain 'Logging' text", badgeLabel)

        // Disable logging
        loggingService.disableLogging(feature)
        assertFalse("Logging should be disabled", loggingService.isLoggingEnabled(feature))

        // Rebuild the panel again
        val finalPanel = panel {
            featureRow(feature, { false }, {}, null, onLoggingChanged)
        }

        // Verify logging badge is gone
        val badgeAfterDisable = findLoggingBadge(finalPanel)
        assertNull("Logging badge should not exist after disabling logging", badgeAfterDisable)
    }

    fun testLoggingBadgeOnlyAppearsForFeaturesWithLoggingCategories() {
        // Get a feature without logging categories
        val featureWithoutLogging = registry.getFeature("populate-arguments")
        assertNotNull("Feature 'populate-arguments' should exist", featureWithoutLogging)
        featureWithoutLogging!!
        assertTrue("Feature should have no logging categories", featureWithoutLogging.loggingCategories.isEmpty())

        // Build a panel with this feature
        val panel = panel {
            featureRow(featureWithoutLogging, { false }, {})
        }

        // Verify no logging badge exists (even if we somehow tried to enable logging)
        val badge = findLoggingBadge(panel)
        assertNull("Logging badge should not exist for features without logging categories", badge)
    }

    fun testCreateLoggingBadgeReturnsNullWhenLoggingDisabled() {
        val feature = registry.getFeature("pytest-failed-line-inspection")
        assertNotNull(feature)
        feature!!

        // Ensure logging is disabled
        if (loggingService.isLoggingEnabled(feature)) {
            loggingService.disableLogging(feature)
        }

        // Create badge should return null
        val badge = FeatureCheckboxBuilder.createLoggingBadge(feature)
        assertNull("createLoggingBadge should return null when logging is disabled", badge)
    }

    fun testCreateLoggingBadgeReturnsComponentWhenLoggingEnabled() {
        val feature = registry.getFeature("pytest-failed-line-inspection")
        assertNotNull(feature)
        feature!!

        // Enable logging
        loggingService.enableLogging(feature)

        try {
            // Create badge should return a component
            val badge = FeatureCheckboxBuilder.createLoggingBadge(feature)
            assertNotNull("createLoggingBadge should return a component when logging is enabled", badge)

            // Verify it's a MaturityTagLabel
            assertTrue(
                "Badge should be a MaturityTagLabel",
                badge is MaturityTagLabel
            )

            // Verify tooltip
            assertEquals(
                "Click to disable logging for this feature",
                badge!!.toolTipText
            )
        } finally {
            loggingService.disableLogging(feature)
        }
    }

    /**
     * Recursively searches for a MaturityTagLabel component with "Logging" text.
     */
    private fun findLoggingBadge(component: JComponent): JComponent? {
        if (component is MaturityTagLabel) {
            // Check if this badge contains "Logging" text
            if (findLabelInComponent(component, "Logging") != null) {
                return component
            }
        }

        // Recursively search children
        for (child in component.components) {
            if (child is JComponent) {
                val found = findLoggingBadge(child)
                if (found != null) return found
            }
        }

        return null
    }

    /**
     * Recursively searches for a JLabel with the specified text.
     */
    private fun findLabelInComponent(component: JComponent, text: String): javax.swing.JLabel? {
        if (component is javax.swing.JLabel && component.text == text) {
            return component
        }

        for (child in component.components) {
            if (child is JComponent) {
                val found = findLabelInComponent(child, text)
                if (found != null) return found
            }
        }

        return null
    }
}
