package com.github.chbndrhnns.intellijplatformplugincopy.intention.unwrap

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.TestBase
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState

class UnwrapSettingsToggleTest : TestBase() {

    fun testUnwrapIntentionHiddenWhenDisabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(old.copy(enableUnwrapIntention = false))

            myFixture.configureByText(
                "a.py",
                """
                from typing import NewType
                UserId = NewType("UserId", int)

                x: int = <caret>UserId(42)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasUnwrap = intentions.any { it.text.startsWith("Unwrap ") }
            assertFalse("Unwrap intention should be hidden when disabled in settings", hasUnwrap)
        } finally {
            svc.loadState(old)
        }
    }

    fun testUnwrapIntentionVisibleWhenEnabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(old.copy(enableUnwrapIntention = true))

            myFixture.configureByText(
                "a.py",
                """
                from typing import NewType
                UserId = NewType("UserId", int)

                x: int = <caret>UserId(42)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            myFixture.findSingleIntention("Unwrap UserId()")
        } finally {
            svc.loadState(old)
        }
    }
}
