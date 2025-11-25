package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class IntroduceCustomTypeParameterTest : TestBase() {

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

        val text = myFixture.file.text
        assertTrue(text.contains("class Customstr(str):"))
        assertTrue(text.contains("def do(val: int | Customstr | None = Customstr(\"2\")):"))
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

        val text = myFixture.file.text
        assertTrue(text.contains("class Customint(int):"))
        assertTrue(text.contains("def do(val: Customint = Customint(1)):"))
    }
}
