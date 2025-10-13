package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.MyPlatformTestCase

internal class TypeMismatchQuickFixIntentionTest : MyPlatformTestCase() {

    fun testQuickfixIsAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: str = Path(<caret>"val")
            """.trimIndent()
        )

//        myFixture.doHighlighting()

        val intentionName = "Show type mismatch details"
        val intentions = myFixture.availableIntentions
        val found = intentions.any { it.text == intentionName }

        assertTrue("Expected to find intention '$intentionName' using daemon-produced highlighting", found)
    }
}
