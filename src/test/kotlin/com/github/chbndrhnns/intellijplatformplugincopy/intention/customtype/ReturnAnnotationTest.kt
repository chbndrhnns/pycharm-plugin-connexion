package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase

class ReturnAnnotationTest : TestBase() {

    fun testReturnAnnotation_UnionType_UpdatesOnlyMatchingPart_WhenInvokedFromReturnExpression_Int() {
        myFixture.configureByText(
            "a.py",
            """
            def get() -> int | str | None:
                return 1<caret>23
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customint(int):
                pass

  
            def get() -> Customint | str | None:
                return Customint(123)
        """.trimIndent()
        )
    }

    fun testReturnAnnotation_UnionType_UpdatesOnlyMatchingPart_WhenInvokedFromReturnExpression_Str() {
        myFixture.configureByText(
            "a.py",
            """
            def get() -> int | str | None:
                return "he<caret>llo"
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customstr(str):
                pass

  
            def get() -> int | Customstr | None:
                return Customstr("hello")
        """.trimIndent()
        )
    }
}
