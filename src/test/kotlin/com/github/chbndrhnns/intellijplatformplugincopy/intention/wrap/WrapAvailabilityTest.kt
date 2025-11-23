package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class WrapAvailabilityTest : TestBase() {

    fun testAssignmentTarget_IntentionNotAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            def func():
                va<caret>l: dict[str, int] = dict(a=1, b=2, c=3)
            """.trimIndent()
        )

        // Check if any "Wrap items with ..." intention is available.
        val intentions = myFixture.availableIntentions.filter { it.text.startsWith("Wrap items with") }
        assertEmpty("Intention 'Wrap items with ...' should NOT be available on variable name", intentions)
    }

    fun testDictConstructor_IntentionNotAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass


            val: dict[str, CustomInt] = di<caret>ct({"a": 1, "b": 2, "c": 3})
            """.trimIndent()
        )

        val intention = myFixture.availableIntentions.find { it.text == "Wrap items with CustomInt()" }
        assertNull(
            "Intention 'Wrap items with CustomInt()' should NOT be available for the dict constructor",
            intention
        )
    }
}
