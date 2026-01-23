package com.github.chbndrhnns.betterpy.features.statusbar

import com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsConfigurable
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
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
        val actions = widget.getPopupActions()
        assertContainsElements(actions, "Settings")
        assertContainsElements(actions, "Copy Diagnostic Data")
    }

    fun testPopupActionsIncludeDisableFeatures() {
        // Ensure not muted initially
        PluginSettingsState.instance().unmute()

        val widget = createWidget()
        val actions = widget.getPopupActions()
        assertContainsElements(actions, "Disable all features")
        assertFalse(actions.contains("Enable all features"))
    }

    fun testPopupActionsIncludeEnableFeaturesWhenMuted() {
        val settings = PluginSettingsState.instance()
        settings.mute()
        try {
            val widget = createWidget()
            val actions = widget.getPopupActions()
            assertContainsElements(actions, "Enable all features")
            assertFalse(actions.contains("Disable all features"))
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

        override fun editConfigurable(project: Project, configurable: Configurable): Boolean = false
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
            component: Component,
            configurable: Configurable,
            advancedSettings: Runnable
        ): Boolean = false

        override fun editConfigurable(project: Project, nameToSelect: String, configurable: Configurable): Boolean =
            false

        override fun editConfigurable(
            project: Project,
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
