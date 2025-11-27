package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import fixtures.TestBase

class SettingsToggleTest : TestBase() {

    fun testIntentionHiddenWhenDisabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(old.copy(enableIntroduceCustomTypeFromStdlibIntention = false))

            myFixture.configureByText(
                "a.py",
                """
                def test_():
                    val: int = 1<caret>234
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasIntention = intentions.any { it.text.startsWith("Introduce custom type") }
            assertFalse("Intention should be hidden when disabled in settings", hasIntention)
        } finally {
            svc.loadState(old)
        }
    }

    fun testIntentionVisibleWhenEnabled() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(old.copy(enableIntroduceCustomTypeFromStdlibIntention = true))

            myFixture.configureByText(
                "a.py",
                """
                def test_():
                    val: int = 1<caret>234
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasIntention = intentions.any { it.text.startsWith("Introduce custom type") }
            assertTrue("Intention should be visible when enabled in settings", hasIntention)
        } finally {
            svc.loadState(old)
        }
    }
}
