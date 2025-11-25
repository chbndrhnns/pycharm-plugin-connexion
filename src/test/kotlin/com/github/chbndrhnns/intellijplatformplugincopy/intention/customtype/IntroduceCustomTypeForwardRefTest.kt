package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class IntroduceCustomTypeForwardRefTest : TestBase() {

    fun testAssignment_UnionInString_UpdatesTextInsideString() {
        myFixture.configureByText(
            "a.py",
            """
            val: "int | str | None" = <caret>2
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val text = myFixture.file.text
        // Expectation: The string content is updated, but quotes remain.
        // Also the class is defined and value wrapped.
        assertTrue(text.contains("class Customint(int):"))
        assertTrue(text.contains("val: \"Customint | str | None\" = Customint(2)"))
    }

    fun testParameter_UnionInString_UpdatesTextInsideString() {
        myFixture.configureByText(
            "a.py",
            """
            def do(val: "int | str | None" = <caret>2):
                pass
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val text = myFixture.file.text
        assertTrue(text.contains("class Customint(int):"))
        assertTrue(text.contains("def do(val: \"Customint | str | None\" = Customint(2)):"))
    }

    fun testAssignment_MixedReferencesAndStrings_UpdatesStringPart() {
        myFixture.configureByText(
            "a.py",
            """
            # This is valid python if types are defined
            val: str | "int" = <caret>2
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val text = myFixture.file.text
        assertTrue(text.contains("class Customint(int):"))
        // Here "int" is a separate string literal. 
        // Should it become "Customint" (string) or Customint (ref)?
        // If we keep string logic consistent, it becomes "Customint".
        assertTrue(text.contains("val: str | \"Customint\" = Customint(2)"))
    }
}
