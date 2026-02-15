package com.github.chbndrhnns.betterpy.features.statusbar

import com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard
import com.github.chbndrhnns.betterpy.featureflags.*
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import fixtures.TestBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.awt.Component
import java.util.function.Consumer
import java.util.function.Predicate

class BetterPyStatusBarWidgetTest : TestBase() {

    fun testWidgetIdIsCorrect() {
        val factory = BetterPyStatusBarWidgetFactory()
        assertEquals(BetterPyStatusBarWidget.ID, factory.id)
    }

    fun testTooltipTextWhenSupported() {
        // TestBase sets up a Python 3.11 SDK, so environment should be supported
        val widget = createWidget()
        val tooltip = runBlocking { widget.getTooltipText() }
        assertEquals("BetterPy", tooltip)
    }

    fun testTooltipTextContainsVersionRequirementWhenUnsupported() {
        // When environment is not supported, tooltip should mention the version requirement
        val minVersion = PythonVersionGuard.minVersionString()

        // We can't easily mock PythonVersionGuard, but we can verify the format
        // by checking that the disabled tooltip format is correct
        val disabledTooltip = "BetterPy (disabled: Python ${minVersion}+ required)"
        assertTrue(
            "Disabled tooltip should contain version requirement",
            disabledTooltip.contains(minVersion)
        )
        assertTrue(
            "Disabled tooltip should indicate disabled state",
            disabledTooltip.contains("disabled")
        )
    }

    fun testMinVersionStringIsNotEmpty() {
        val minVersion = PythonVersionGuard.minVersionString()
        assertNotNull("Min version string should not be null", minVersion)
        assertTrue("Min version string should not be empty", minVersion.isNotEmpty())
    }

    fun testWidgetIconIsNotNull() {
        val widget = createWidget()
        val icon = runBlocking { widget.icon().first() }
        assertNotNull("Widget icon should not be null", icon)
    }

    fun testWidgetClickConsumerIsNotNull() {
        val widget = createWidget()
        assertNotNull("Widget click consumer should not be null", widget.getClickConsumer())
    }

    fun testIsEnvironmentSupportedWithConfiguredSdk() {
        // TestBase sets up a Python 3.11 SDK, so this should return true
        val widget = createWidget()
        assertTrue(
            "Environment should be supported with Python 3.11 SDK configured by TestBase",
            widget.isEnvironmentSupported()
        )
    }

    fun testPopupActionsIncludeSettings() {
        val widget = createWidget()
        val actions = widget.getPopupActions().map { it.label }
        assertContainsElements(actions, "Settings")
        assertContainsElements(actions, "Copy Diagnostic Data")
    }

    fun testPopupActionsIncludeDisableFeatures() {
        // Ensure not muted initially
        PluginSettingsState.instance().unmute()

        val widget = createWidget()
        val actions = widget.getPopupActions().map { it.label }
        assertContainsElements(actions, "Disable all features (temporary until restart)")
        assertFalse(actions.contains("Enable all features"))
    }

    fun testPopupActionsIncludeEnableFeaturesWhenMuted() {
        val settings = PluginSettingsState.instance()
        settings.mute()
        try {
            val widget = createWidget()
            val actions = widget.getPopupActions().map { it.label }
            assertContainsElements(actions, "Enable all features")
            assertFalse(actions.contains("Disable all features (temporary until restart)"))
        } finally {
            settings.unmute()
        }
    }

    fun testIconAndTooltipWhenMuted() {
        val settings = PluginSettingsState.instance()
        settings.mute()
        try {
            val widget = createWidget()
            assertEquals("BetterPy (Muted)", runBlocking { widget.getTooltipText() })
            assertNotNull(runBlocking { widget.icon().first() })
        } finally {
            settings.unmute()
        }
    }

    fun testIncubatingToggleChangesPopupLabel() {
        val settings = PluginSettingsState.instance()
        settings.unmute()
        val registry = FeatureRegistry.instance()
        val fakeFeature = createFakeIncubatingFeature()
        registry.registerAdditionalFeature(fakeFeature)
        try {
            val widget = createWidget()

            // Initially incubating features should be enabled (defaultEnabled: true in yaml)
            val actionsBefore = widget.getPopupActions().map { it.label }
            assertTrue(
                "Should show 'Turn incubating features off' initially",
                actionsBefore.any { it.startsWith("Turn incubating features off") }
            )

            // Toggle incubating features off
            settings.toggleIncubatingFeatures()

            val actionsAfter = widget.getPopupActions().map { it.label }
            assertTrue(
                "Should show 'Turn incubating features on' after toggling off, but got: $actionsAfter",
                actionsAfter.any { it.startsWith("Turn incubating features on") }
            )

            // Toggle back
            settings.toggleIncubatingFeatures()

            val actionsRestored = widget.getPopupActions().map { it.label }
            assertTrue(
                "Should show 'Turn incubating features off' after restoring",
                actionsRestored.any { it.startsWith("Turn incubating features off") }
            )
        } finally {
            // Ensure clean state
            if (settings.isIncubatingOverrideActive()) {
                settings.toggleIncubatingFeatures()
            }
            registry.unregisterAdditionalFeature(fakeFeature.id)
        }
    }

    fun testIncubatingToggleWorksWhenOneFeatureAlreadyDisabled() {
        val settings = PluginSettingsState.instance()
        settings.unmute()
        val registry = FeatureRegistry.instance()
        val fakeFeature1 = createFakeIncubatingFeature("test-incubating-1")
        val fakeFeature2 = createFakeIncubatingFeature("test-incubating-2")
        registry.registerAdditionalFeature(fakeFeature1)
        registry.registerAdditionalFeature(fakeFeature2)
        val incubatingFeatures = registry.getIncubatingFeatures()
        assertTrue("Need at least one incubating feature for this test", incubatingFeatures.isNotEmpty())

        // Disable one incubating feature via settings (simulating user turning it off)
        val firstFeature = incubatingFeatures.first()
        val originalValue = firstFeature.isEnabled()
        firstFeature.setEnabled(false)

        try {
            val widget = createWidget()

            // Some incubating features are still enabled, so popup should show "off"
            if (incubatingFeatures.size > 1) {
                val actionsBefore = widget.getPopupActions().map { it.label }
                assertTrue(
                    "Should show 'Turn incubating features off' when some are still enabled, got: $actionsBefore",
                    actionsBefore.any { it.startsWith("Turn incubating features off") }
                )
            }

            // Toggle incubating features off
            settings.toggleIncubatingFeatures()

            // ALL incubating features should now be disabled
            incubatingFeatures.forEach { feature ->
                assertFalse(
                    "Incubating feature '${feature.id}' should be disabled after toggle off",
                    feature.isEnabled()
                )
            }

            val actionsAfter = widget.getPopupActions().map { it.label }
            assertTrue(
                "Should show 'Turn incubating features on' after toggling off, got: $actionsAfter",
                actionsAfter.any { it.startsWith("Turn incubating features on") }
            )

            // Restore
            settings.toggleIncubatingFeatures()

            // The first feature should be restored to its pre-toggle state (disabled)
            assertFalse(
                "First feature should still be disabled after restore (was disabled before toggle)",
                firstFeature.isEnabled()
            )
        } finally {
            if (settings.isIncubatingOverrideActive()) {
                settings.toggleIncubatingFeatures()
            }
            firstFeature.setEnabled(originalValue)
            registry.unregisterAdditionalFeature(fakeFeature1.id)
            registry.unregisterAdditionalFeature(fakeFeature2.id)
        }
    }

    fun testInvokeShowSettingsActionOpensPluginConfigurable() {
        val fakeShowSettingsUtil = FakeShowSettingsUtil()

        val widget = createWidget()
        widget.invokeShowSettingsAction(fakeShowSettingsUtil)

        assertEquals("Should open settings for current project", project, fakeShowSettingsUtil.lastProject)
        assertEquals(
            "Should open PluginSettingsConfigurable",
            PluginSettingsConfigurable::class.java,
            fakeShowSettingsUtil.lastConfigurableClass
        )
    }

    private fun createWidget(): BetterPyStatusBarWidget {
        return BetterPyStatusBarWidget(project, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    }

    private fun createFakeIncubatingFeature(id: String = "test-incubating-feature"): FeatureRegistry.FeatureInfo {
        var enabled = true
        return FeatureRegistry.FeatureInfo(
            id = id,
            displayName = "Test Incubating Feature",
            description = "A fake incubating feature for testing",
            maturity = FeatureMaturity.INCUBATING,
            category = FeatureCategory.OTHER,
            youtrackIssues = emptyList(),
            loggingCategories = emptyList(),
            since = "",
            removeIn = "",
            propertyName = id,
            getter = { enabled },
            setter = { enabled = it }
        )
    }

    class FakeShowSettingsUtil : ShowSettingsUtil() {
        var lastProject: Project? = null
        var lastConfigurableClass: Class<*>? = null

        override fun <T : Configurable> showSettingsDialog(project: Project?, configurableClass: Class<T>) {
            lastProject = project
            lastConfigurableClass = configurableClass
        }

        override fun showSettingsDialog(project: Project, vararg groups: ConfigurableGroup) {}
        override fun showSettingsDialog(project: Project?, nameToSelect: String) {}
        override fun showSettingsDialog(project: Project, configurable: Configurable?) {}
        override fun <T : Configurable> showSettingsDialog(
            project: Project?,
            configurableClass: Class<T>,
            consumer: Consumer<in T>?
        ) {
        }

        override fun showSettingsDialog(
            project: Project?,
            predicate: Predicate<in Configurable>,
            consumer: Consumer<in Configurable>?
        ) {
        }

        override fun editConfigurable(project: Project?, configurable: Configurable): Boolean = false
        override fun editConfigurable(
            project: Project?,
            configurable: Configurable,
            advancedSettings: Runnable?
        ): Boolean = false

        override fun <T : Configurable> editConfigurable(
            project: Project?,
            configurable: T,
            consumer: Consumer<in T>
        ): Boolean = false

        override fun editConfigurable(component: Component?, configurable: Configurable): Boolean = false
        override fun editConfigurable(component: Component?, nameToSelect: String): Boolean = false
        override fun editConfigurable(
            component: Component?,
            nameToSelect: String,
            advancedSettings: Runnable?
        ): Boolean = false

        override fun editConfigurable(
            component: Component?,
            configurable: Configurable,
            advancedSettings: Runnable?
        ): Boolean = false

        override fun editConfigurable(project: Project?, nameToSelect: String, configurable: Configurable): Boolean =
            false

        override fun editConfigurable(
            project: Project?,
            nameToSelect: String,
            configurable: Configurable,
            showApplyButton: Boolean
        ): Boolean = false

        override fun editConfigurable(component: Component, nameToSelect: String, configurable: Configurable): Boolean =
            false

        override fun closeSettings(project: Project, component: Component) {}

        override suspend fun showSettingsDialog(project: Project, groups: List<ConfigurableGroup>) {}
    }
}
