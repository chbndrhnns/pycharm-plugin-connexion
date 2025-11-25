package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class WrapForwardRefTest : TestBase() {

    fun testParameterDefault_UnionForwardRef_NoWrapOffered() {
        myFixture.configureByText(
            "a.py",
            """
            def do(val: "int | str | None" = <caret>2):
                pass
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val wrapIntention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with") }
        assertNull(
            "Wrap intention should not be offered when value matches one of the union types in forward ref",
            wrapIntention
        )
    }

    fun testAssignment_UnionForwardRef_NoWrapOffered() {
        myFixture.configureByText(
            "a.py",
            """
            val: "int | str | None" = <caret>2
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val wrapIntention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with") }
        assertNull(
            "Wrap intention should not be offered when value matches one of the union types in forward ref",
            wrapIntention
        )
    }
}
