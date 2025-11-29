package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class RequiredArgumentsSettingsToggleTest : TestBase() {

    fun testIntentionHiddenWhenDisabled() {
        withPluginSettings({ it.copy(enablePopulateRequiredArgumentsIntention = false) }) {
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
        }
    }

    fun testIntentionVisibleWhenEnabled() {
        withPluginSettings({ it.copy(enablePopulateRequiredArgumentsIntention = true) }) {
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
        }
    }
}
