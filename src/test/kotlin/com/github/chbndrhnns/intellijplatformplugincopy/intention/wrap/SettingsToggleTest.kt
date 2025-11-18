package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState

class SettingsToggleTest : TestBase() {

    fun testWrapIntentionHiddenWhenDisabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(
                PluginSettingsState.State(
                    enableWrapIntention = false,
                    enableUnwrapIntention = old.enableUnwrapIntention,
                )
            )

            myFixture.configureByText(
                "a.py",
                """
                from pathlib import Path
                a: str = Path(<caret>"val")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasWrap = intentions.any { it.text.startsWith("Wrap with") }
            assertFalse("Wrap intention should be hidden when disabled in settings", hasWrap)
        } finally {
            svc.loadState(old)
        }
    }

    fun testWrapIntentionVisibleWhenEnabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(
                PluginSettingsState.State(
                    enableWrapIntention = true,
                    enableUnwrapIntention = old.enableUnwrapIntention,
                )
            )

            myFixture.configureByText(
                "a.py",
                """
                from pathlib import Path
                a: str = Path(<caret>"val")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            myFixture.findSingleIntention("Wrap with str()")
        } finally {
            svc.loadState(old)
        }
    }
}
