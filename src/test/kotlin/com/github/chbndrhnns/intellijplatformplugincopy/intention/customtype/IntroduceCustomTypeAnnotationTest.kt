package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class IntroduceCustomTypeAnnotationTest : TestBase() {

    fun testAnnotatedAssignment_UnionType_UpdatesOnlyMatchingPart() {
        myFixture.configureByText(
            "a.py",
            """
            val: int | str | None = <caret>2
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val text = myFixture.file.text
        assertTrue(text.contains("class Customint(int):"))
        assertTrue(text.contains("val: Customint | str | None = Customint(2)"))
    }

    fun testAnnotatedParameter_UnionType_UpdatesOnlyMatchingPart() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomWrapper: ...


            def f(x: CustomWrapper | str) -> None:
                pass
            
            
            f("a<caret>bc")
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customstr(str):
                pass


            class CustomWrapper: ...


            def f(x: CustomWrapper | Customstr) -> None:
                pass


            f(Customstr("abc"))
        """.trimIndent()
        )
    }

    fun testAnnotatedAssignment_UnionType_String_UpdatesOnlyMatchingPart() {
        myFixture.configureByText(
            "a.py",
            """
            val: int | str | None = <caret>"hello"
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        val text = myFixture.file.text
        assertTrue(text.contains("class Customstr(str):"))
        assertTrue(text.contains("val: int | Customstr | None = Customstr(\"hello\")"))
    }
}
