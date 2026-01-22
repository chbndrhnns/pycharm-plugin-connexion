package com.github.chbndrhnns.intellijplatformplugincopy.featureflags

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSettingsStateTest {

    @Test
    fun `test mute disables all features and unmute restores them`() {
        val settings = PluginSettingsState()

        // Initial state: all true by default
        assertTrue(settings.state.enablePopulateArgumentsIntention)

        // Change one to false to verify restoration
        settings.state.enableWrapWithExpectedTypeIntention = false

        settings.mute()

        assertTrue("Should be muted", settings.isMuted())
        assertFalse("Feature should be disabled", settings.state.enablePopulateArgumentsIntention)
        assertFalse("Feature should be disabled", settings.state.enableWrapWithExpectedTypeIntention)

        settings.unmute()

        assertFalse("Should not be muted", settings.isMuted())
        assertTrue("Feature should be restored to true", settings.state.enablePopulateArgumentsIntention)
        assertFalse("Feature should be restored to false", settings.state.enableWrapWithExpectedTypeIntention)
    }

    @Test
    fun `test loadState restores if muted`() {
        val settings = PluginSettingsState()

        // Create a state that looks like it was muted and saved
        // (Use copy logic manually as we cannot easily mock the internal state without access)
        // But we can construct a State object.

        val original = PluginSettingsState.State(
            enablePopulateArgumentsIntention = true,
            enableWrapWithExpectedTypeIntention = false
        )

        val mutedState = PluginSettingsState.State(
            enablePopulateArgumentsIntention = false,
            enableWrapWithExpectedTypeIntention = false,
            originalState = original
        )

        settings.loadState(mutedState)

        // It should have auto-unmuted
        assertFalse("Should be unmuted after load", settings.isMuted())
        assertTrue("Feature should be restored", settings.state.enablePopulateArgumentsIntention)
        assertFalse("Feature should be restored", settings.state.enableWrapWithExpectedTypeIntention)
    }
}
