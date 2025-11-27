package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import fixtures.TestBase

class KwOnlyArgumentsSettingsToggleTest : TestBase() {

    fun testIntentionHiddenWhenDisabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(
                old.copy(
                    enablePopulateKwOnlyArgumentsIntention = false,
                    enablePopulateRequiredArgumentsIntention = old.enablePopulateRequiredArgumentsIntention,
                ),
            )

            myFixture.configureByText(
                "a.py",
                """
                def foo(a, b):
                    pass

                foo(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasIntention = intentions.any { it.text == "Populate missing arguments with '...'" }
            assertFalse("Intention should be hidden when disabled in settings", hasIntention)
        } finally {
            svc.loadState(old)
        }
    }

    fun testIntentionVisibleWhenEnabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(
                old.copy(
                    enablePopulateKwOnlyArgumentsIntention = true,
                    enablePopulateRequiredArgumentsIntention = old.enablePopulateRequiredArgumentsIntention,
                ),
            )

            myFixture.configureByText(
                "a.py",
                """
                def foo(a, b):
                    pass

                foo(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasIntention = intentions.any { it.text == "Populate missing arguments with '...'" }
            assertTrue("Intention should be visible when enabled in settings", hasIntention)
        } finally {
            svc.loadState(old)
        }
    }
}
