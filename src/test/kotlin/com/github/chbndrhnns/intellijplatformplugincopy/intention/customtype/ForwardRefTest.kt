package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase

class ForwardRefTest : TestBase() {

    fun testAssignment_UnionInString_UpdatesTextInsideString() {
        myFixture.configureByText(
            "a.py",
            """
            val: "int | str | None" = <caret>2
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customint(int):
                pass
            
            
            val: "Customint | str | None" = Customint(2)
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            class Customint(int):
                pass
            
            
            def do(val: "Customint | str | None" = Customint(2)):
                pass
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            class Customint(int):
                pass
            
            
            # This is valid python if types are defined
            val: str | "Customint" = Customint(2)
        """.trimIndent()
        )
    }
}
