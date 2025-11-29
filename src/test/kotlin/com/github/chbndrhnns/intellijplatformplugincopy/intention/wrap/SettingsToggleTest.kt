package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class SettingsToggleTest : TestBase() {

    fun testWrapIntentionHiddenWhenDisabled() {
        withPluginSettings({ it.copy(enableWrapWithExpectedTypeIntention = false) }) {
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
        }
    }

    fun testWrapIntentionVisibleWhenEnabled() {
        withPluginSettings({ it.copy(enableWrapWithExpectedTypeIntention = true) }) {
            myFixture.configureByText(
                "a.py",
                """
                from pathlib import Path
                a: str = Path(<caret>"val")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            myFixture.findSingleIntention("Wrap with str()")
        }
    }
}
