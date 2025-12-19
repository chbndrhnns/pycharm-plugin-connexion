package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class SettingsToggleTest : TestBase() {

    private fun configureTriggeringFile() {
        myFixture.configureByText(
            "a.py",
            """
            from dataclasses import dataclass

            @dataclass
            class A:
                x: int

            def f():
                A(<caret>)
            """.trimIndent()
        )
    }

    fun testIntentionHiddenWhenDisabled() {
        withPluginSettings({ enablePopulateArgumentsIntention = false }) {
            configureTriggeringFile()
            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasIntention = intentions.any { it.text == "BetterPy: Populate arguments..." }
            assertFalse("Populate arguments intention should be hidden when disabled in settings", hasIntention)
        }
    }

    fun testIntentionVisibleWhenEnabled() {
        withPluginSettings({ enablePopulateArgumentsIntention = true }) {
            configureTriggeringFile()
            myFixture.doHighlighting()
            val intentions = myFixture.availableIntentions
            val hasIntention = intentions.any { it.text == "BetterPy: Populate arguments..." }
            assertTrue("Populate arguments intention should be visible when enabled in settings", hasIntention)
        }
    }
}
