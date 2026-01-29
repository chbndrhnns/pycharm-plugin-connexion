package com.github.chbndrhnns.betterpy.features.intentions

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class CaptureUnusedParametersIntentionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        PluginSettingsState.instance().state.enableCaptureUnusedParametersIntention = true
    }

    fun testCaptureFromSignature() {
        myFixture.doIntentionTest(
            "capture_signature.py",
            """
            def greet(name, t<caret>itle, suffix):
                return name
            """,
            """
            def greet(name, title, suffix):
                _ = title, suffix
                return name
            """,
            "BetterPy: Capture unused parameters on _"
        )
    }

    fun testCaptureFromBody() {
        myFixture.doIntentionTest(
            "capture_body.py",
            """
            def greet(name, title, suffix):
                message = f"Hi {name}"
                return m<caret>essage
            """,
            """
            def greet(name, title, suffix):
                _ = title, suffix
                message = f"Hi {name}"
                return message
            """,
            "BetterPy: Capture unused parameters on _"
        )
    }

    fun testDisabledViaSettings() {
        withPluginSettings({ enableCaptureUnusedParametersIntention = false }) {
            myFixture.assertIntentionNotAvailable(
                "capture_disabled.py",
                """
                def greet(name, title, suffix):
                    return n<caret>ame
                """,
                "BetterPy: Capture unused parameters on _"
            )
        }
    }
}
