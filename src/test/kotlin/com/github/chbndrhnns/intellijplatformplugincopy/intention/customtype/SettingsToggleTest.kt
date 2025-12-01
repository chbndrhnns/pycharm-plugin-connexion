package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class SettingsToggleTest : TestBase() {

    fun testIntentionHiddenWhenDisabled() {
        withPluginSettings({ enableIntroduceCustomTypeFromStdlibIntention = false }) {
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
        }
    }

    fun testIntentionVisibleWhenEnabled() {
        withPluginSettings({ enableIntroduceCustomTypeFromStdlibIntention = true }) {
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
        }
    }
}
