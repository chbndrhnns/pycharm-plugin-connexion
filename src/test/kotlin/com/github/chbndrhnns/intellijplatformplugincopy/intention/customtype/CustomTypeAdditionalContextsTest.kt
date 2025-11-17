package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.TestBase

/**
 * Additional applicability contexts for IntroduceCustomTypeFromStdlibIntention
 * beyond simple annotations.
 */
class CustomTypeAdditionalContextsTest : TestBase() {

    fun testDataclassCall_KeywordArgValue_UsesIntAndWrapsArgument() {
        myFixture.configureByText(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class Data:
                val: int
                
            def test_():
                Data(val=<caret>1)

            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class CustomInt(int):"))
        assertTrue(result.contains("Data(val=CustomInt(1))"))
    }

    fun testStringLiteral_NoAnnotation_WrapsWithCustomStr() {
        myFixture.configureByText(
            "a.py",
            """
            def expect_str(s: str) -> None:
                ...

            expect_str("ab<caret>c")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class CustomStr(str):"))
        assertTrue(result.contains("expect_str(CustomStr(\"abc\"))"))
    }

    fun testStringLiteralAssignment_NoAnnotation_WrapsWithCustomStr() {
        myFixture.configureByText(
            "a.py",
            """
            val = "s<caret>tr"
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class CustomStr(str):"))
        assertTrue(result.contains("val = CustomStr(\"str\")"))
    }

    fun testFloatLiteralAssignment_UsesFloatAndWrapsValue() {
        myFixture.configureByText(
            "a.py",
            """
            val = 4567.<caret>6
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from float")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class CustomFloat(float):"))
        assertTrue(result.contains("val = CustomFloat(4567.6)"))
    }
}
