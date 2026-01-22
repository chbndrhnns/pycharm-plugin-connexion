package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.unwrap

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class UnwrapSettingsToggleTest : TestBase() {

    fun testUnwrapIntentionHiddenWhenDisabled() {
        withPluginSettings({ enableUnwrapToExpectedTypeIntention = false }) {
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
            val hasUnwrap = intentions.any { it.text.startsWith("BetterPy: Unwrap ") }
            assertFalse("BetterPy: Unwrap intention should be hidden when disabled in settings", hasUnwrap)
        }
    }

    fun testUnwrapIntentionVisibleWhenEnabled() {
        withPluginSettings({ enableUnwrapToExpectedTypeIntention = true }) {
            myFixture.configureByText(
                "a.py",
                """
                from typing import NewType
                UserId = NewType("UserId", int)

                x: int = <caret>UserId(42)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            myFixture.findSingleIntention("BetterPy: Unwrap UserId()")
        }
    }
}
