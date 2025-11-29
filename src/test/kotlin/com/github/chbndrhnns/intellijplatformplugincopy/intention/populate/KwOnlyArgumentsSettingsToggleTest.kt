package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

//
class KwOnlyArgumentsSettingsToggleTest : TestBase() {

    fun testIntentionHiddenWhenDisabled() {
        withPluginSettings({ it.copy(enablePopulateKwOnlyArgumentsIntention = false) }) {
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
        }
    }

    fun testIntentionVisibleWhenEnabled() {
        withPluginSettings({ it.copy(enablePopulateKwOnlyArgumentsIntention = true) }) {
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
        }
    }
}
