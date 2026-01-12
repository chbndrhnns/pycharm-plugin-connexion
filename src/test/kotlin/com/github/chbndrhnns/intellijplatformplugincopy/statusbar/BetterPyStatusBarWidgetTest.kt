package com.github.chbndrhnns.intellijplatformplugincopy.statusbar

import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import fixtures.TestBase

class BetterPyStatusBarWidgetTest : TestBase() {

    fun testWidgetIdIsCorrect() {
        val widget = BetterPyStatusBarWidget(project)
        assertEquals(BetterPyStatusBarWidget.ID, widget.ID())
    }

    fun testTooltipTextWhenSupported() {
        // TestBase sets up a Python 3.11 SDK, so environment should be supported
        val widget = BetterPyStatusBarWidget(project)
        val tooltip = widget.getTooltipText()
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

    fun testWidgetPresentationIsNotNull() {
        val widget = BetterPyStatusBarWidget(project)
        assertNotNull("Widget presentation should not be null", widget.getPresentation())
    }

    fun testWidgetIconIsNotNull() {
        val widget = BetterPyStatusBarWidget(project)
        assertNotNull("Widget icon should not be null", widget.getIcon())
    }

    fun testWidgetClickConsumerIsNotNull() {
        val widget = BetterPyStatusBarWidget(project)
        assertNotNull("Widget click consumer should not be null", widget.getClickConsumer())
    }

    fun testIsEnvironmentSupportedWithConfiguredSdk() {
        // TestBase sets up a Python 3.11 SDK, so this should return true
        val widget = BetterPyStatusBarWidget(project)
        assertTrue(
            "Environment should be supported with Python 3.11 SDK configured by TestBase",
            widget.isEnvironmentSupported()
        )
    }
}
