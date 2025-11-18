package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

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

    fun testDataclassField_WrapsKeywordArgumentUsages() {
        myFixture.configureByText(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: i<caret>nt


            def do():
                D(product_id=123)
                D(product_id=456)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("product_id: Productid"))
        assertTrue(result.contains("D(product_id=Productid(123))"))
        assertTrue(result.contains("D(product_id=Productid(456))"))
    }

    fun testDataclassField_WrapsPositionalArgumentUsages() {
        myFixture.configureByText(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: i<caret>nt
                other: str


            def do():
                D(123, "x")
                D(456, other="y")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("product_id: Productid"))
        assertTrue(result.contains("D(Productid(123), \"x\")"))
        assertTrue(result.contains("D(Productid(456), other=\"y\")"))
    }

    fun testDataclassCall_IntroduceFromKeywordValue_UpdatesFieldAndAllUsages() {
        myFixture.configureByText(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: int


            def do():
                D(product_id=12<caret>3)
                D(product_id=456)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("product_id: Productid"))
        assertTrue(result.contains("D(product_id=Productid(123))"))
        assertTrue(result.contains("D(product_id=Productid(456))"))
    }

    fun testDataclassCall_IntroduceFromPositionalValue_UpdatesFieldAndUsages() {
        myFixture.configureByText(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: int
                other: str


            def do():
                D(12<caret>3, "a")
                D(456, "b")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("product_id: Productid"))
        assertTrue(result.contains("D(Productid(123), \"a\")"))
        assertTrue(result.contains("D(Productid(456), \"b\")"))
    }

    fun testDataclassCall_DoesNotDoubleWrapIfAlreadyCustom() {
        myFixture.configureByText(
            "a.py",
            """
            import dataclasses


            class Productid(int):
                pass


            @dataclasses.dataclass
            class D:
                product_id: int


            def do():
                D(product_id=Productid(12<caret>3))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        // Depending on future behaviour, we at least avoid double-wrapping.
        assertFalse(result.contains("Productid(Productid(123))"))
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

    fun testPydanticModelField_WrapsKeywordArgumentUsages() {
        myFixture.configureByText(
            "a.py",
            """
            import pydantic


            class D(pydantic.BaseModel):
                product_id: i<caret>nt


            def do():
                D(product_id=123)
                D(product_id=456)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("product_id: Productid"))
        assertTrue(result.contains("D(product_id=Productid(123))"))
        assertTrue(result.contains("D(product_id=Productid(456))"))
    }

    fun testPydanticModelCall_IntroduceFromKeywordValue_UpdatesFieldAndUsages() {
        myFixture.configureByText(
            "a.py",
            """
            from pydantic import BaseModel


            class D(BaseModel):
                product_id: int


            def do():
                D(product_id=12<caret>3)
                D(product_id=456)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("product_id: Productid"))
        assertTrue(result.contains("D(product_id=Productid(123))"))
        assertTrue(result.contains("D(product_id=Productid(456))"))
    }

    fun testPydanticModelCall_IntroduceFromPositionalValue_UpdatesFieldAndUsages() {
        myFixture.configureByText(
            "a.py",
            """
            from pydantic import BaseModel


            class D(BaseModel):
                product_id: int
                other: str


            def do():
                D(12<caret>3, "a")
                D(456, "b")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("product_id: Productid"))
        assertTrue(result.contains("D(Productid(123), \"a\")"))
        assertTrue(result.contains("D(Productid(456), \"b\")"))
    }

    /**
     * Cross-module scenario exercising both class placement and import
     * addition. Kept as documentation only: the lightweight fixture does not
     * reliably resolve dataclass calls across files, so this test is not run
     * by default (name does not start with `test`).
     */
    fun ignoredDataclassCrossModule_AddsImportAtUsageSite() {
        myFixture.configureByText(
            "model.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: int
            """.trimIndent(),
        )

        myFixture.configureByText(
            "usage.py",
            """
            from model import D


            def do():
                D(product_id=12<caret>3)
            """.trimIndent(),
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val model = myFixture.findFileInTempDir("model.py")
        val usage = myFixture.findFileInTempDir("usage.py")

        val modelText = String(model.contentsToByteArray())
        val usageText = String(usage.contentsToByteArray())

        // Custom type should be introduced alongside the dataclass declaration.
        assertTrue(modelText.contains("class Productid(int):"))
        assertTrue(modelText.contains("product_id: Productid"))

        // Usage site should import the new custom type and use it at the call-site.
        assertTrue(
            usageText.contains("from model import D, Productid") ||
                    (usageText.contains("from model import D") && usageText.contains("from model import Productid"))
        )
        assertTrue(usageText.contains("D(product_id=Productid(123))"))
    }

    /**
     * Cross-module scenario: declaration and usage in different modules. Kept as
     * a documentation/guard test but not executed by default (name does not
     * start with `test`) because the lightweight fixture does not fully model
     * cross-file resolution the same way as a real IDE project.
     */
    fun ignoredDataclassCrossModule_CustomTypeLivesWithDeclaration() {
        myFixture.configureByText(
            "model.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: int
            """.trimIndent(),
        )

        myFixture.configureByText(
            "usage.py",
            """
            from model import D


            def do():
                D(product_id=12<caret>3)
            """.trimIndent(),
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val model = myFixture.findFileInTempDir("model.py")
        val usage = myFixture.findFileInTempDir("usage.py")

        val modelText = String(model.contentsToByteArray())
        val usageText = String(usage.contentsToByteArray())

        // Custom type should be introduced in the declaration module, not in the
        // usage module. We keep the assertion intentionally minimal here: the
        // core requirement is that the new type lives alongside the dataclass
        // declaration when declaration and usage are in different modules.
        assertTrue(modelText.contains("class Productid(int):"))
        assertFalse(usageText.contains("class Productid(int):"))
    }
}
