package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase

class ParameterTest : TestBase() {

    fun testParameterDefaultValue_UnionType_UpdatesAnnotationAndWrapsValue() {
        myFixture.configureByText(
            "a.py",
            """
            def do(val: int | str | None = <caret>"2"): 
                pass
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customstr(str):
                pass
            
            
            def do(val: int | Customstr | None = Customstr("2")): 
                pass
            """.trimIndent()
        )
    }

    fun testParameterDefaultValue_SimpleType_UpdatesAnnotationAndWrapsValue() {
        myFixture.configureByText(
            "a.py",
            """
            def do(val: int = <caret>1): 
                pass
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customint(int):
                pass
            
            
            def do(val: Customint = Customint(1)): 
                pass
        """.trimIndent()
        )
    }
}
