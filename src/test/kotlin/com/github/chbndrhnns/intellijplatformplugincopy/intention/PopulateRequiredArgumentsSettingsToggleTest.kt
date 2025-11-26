package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState

//
class PopulateRequiredArgumentsSettingsToggleTest : TestBase() {

    fun testIntentionHiddenWhenDisabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(old.copy(enablePopulateRequiredArgumentsIntention = false))

            myFixture.configureByText(
                "a.py",
                """
                def foo(a, b, c=3):
                    pass

                foo(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasIntention = intentions.any { it.text == "Populate required arguments with '...'" }
            assertFalse("Intention should be hidden when disabled in settings", hasIntention)
        } finally {
            svc.loadState(old)
        }
    }

    fun testIntentionVisibleWhenEnabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(old.copy(enablePopulateRequiredArgumentsIntention = true))

            myFixture.configureByText(
                "a.py",
                """
                def foo(a, b, c=3):
                    pass

                foo(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasIntention = intentions.any { it.text == "Populate required arguments with '...'" }
            assertTrue("Intention should be visible when enabled in settings", hasIntention)
        } finally {
            svc.loadState(old)
        }
    }
}
