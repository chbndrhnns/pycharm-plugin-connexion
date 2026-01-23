package com.github.chbndrhnns.betterpy.features.intentions.wrap

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
                "BetterPy: Wrap with"
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
                "BetterPy: Wrap with str()"
            )
        }
    }
}
