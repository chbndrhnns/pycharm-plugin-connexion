package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.assertIntentionNotAvailable

class SettingsToggleTest : TestBase() {

    fun testWrapIntentionHiddenWhenDisabled() {
        withPluginSettings({ enableWrapWithExpectedTypeIntention = false }) {
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
        withPluginSettings({ enableWrapWithExpectedTypeIntention = true }) {
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
