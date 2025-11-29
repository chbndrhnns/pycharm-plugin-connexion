package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.assertIntentionNotAvailable

//
class SettingsToggleTest : TestBase() {

    fun testWrapIntentionHiddenWhenDisabled() {
        withPluginSettings({ it.copy(enableWrapWithExpectedTypeIntention = false) }) {
            myFixture.assertIntentionNotAvailable(
                "a.py",
                """
                from pathlib import Path
                a: str = Path(<caret>"val")
                """,
                "Wrap with"
            )
        }
    }

    fun testWrapIntentionVisibleWhenEnabled() {
        withPluginSettings({ it.copy(enableWrapWithExpectedTypeIntention = true) }) {
            myFixture.assertIntentionAvailable(
                "a.py",
                """
                from pathlib import Path
                a: str = Path(<caret>"val")
                """,
                "Wrap with str()"
            )
        }
    }
}
