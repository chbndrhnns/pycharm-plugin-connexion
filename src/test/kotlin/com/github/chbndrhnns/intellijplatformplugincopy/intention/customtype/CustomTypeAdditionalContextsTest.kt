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
        assertTrue(result.contains("class Customint(int):"))
        assertTrue(result.contains("Data(val=Customint(1))"))
    }

    fun testDataclassField_WithSnakeCaseName_UsesFieldNameForClass() {
        myFixture.configureByText(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: i<caret>nt
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        println("[DEBUG_LOG] Resulting file (dataclass field):\n$result")
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("product_id: Productid"))
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
        assertTrue(result.contains("class Customstr(str):"))
        assertTrue(result.contains("expect_str(Customstr(\"abc\"))"))
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
        assertTrue(result.contains("class Customstr(str):"))
        assertTrue(result.contains("val = Customstr(\"str\")"))
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
        assertTrue(result.contains("class Customfloat(float):"))
        assertTrue(result.contains("val = Customfloat(4567.6)"))
    }

    fun testIntAssignment_WithSnakeCaseName_UsesTargetNameForClass() {
        myFixture.configureByText(
            "a.py",
            """
            product_id = 12<caret>34
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        println("[DEBUG_LOG] Resulting file (int assignment):\n$result")
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("product_id = Productid(1234)"))
    }

    fun testKeywordArgument_WithSnakeCaseName_UsesKeywordForClass() {
        myFixture.configureByText(
            "a.py",
            """
            def do(my_arg: int) -> None:
                ...

            def test_():
                do(my_arg=12<caret>34)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        println("[DEBUG_LOG] Resulting file (keyword arg):\n$result")
        assertTrue(result.contains("class Myarg(int):"))
        assertTrue(result.contains("do(my_arg=Myarg(1234))"))
    }
}
