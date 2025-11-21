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

        myFixture.checkResult(
            """
            import dataclasses


            class Customint(int):
                pass


            @dataclasses.dataclass
            class Data:
                val: Customint
                
            def test_():
                Data(val=Customint(1))

            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            import dataclasses
            
            
            class ProductId(int):
                pass
            
            
            @dataclasses.dataclass
            class D:
                product_id: ProductId
            
            
            def do():
                D(product_id=ProductId(123))
                D(product_id=ProductId(456))
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            import dataclasses
            
            
            class ProductId(int):
                pass
            
            
            @dataclasses.dataclass
            class D:
                product_id: ProductId
                other: str
            
            
            def do():
                D(ProductId(123), "x")
                D(ProductId(456), other="y")
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            import dataclasses
            
            
            class ProductId(int):
                pass
            
            
            @dataclasses.dataclass
            class D:
                product_id: ProductId
            
            
            def do():
                D(product_id=ProductId(123))
                D(product_id=ProductId(456))
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
                import dataclasses

                
                class ProductId(int):
                    pass
                
                
                @dataclasses.dataclass
                class D:
                    product_id: ProductId
                    other: str
                
                
                def do():
                    D(ProductId(123), "a")
                    D(ProductId(456), "b")
            """.trimIndent()
        )
    }

    fun testDataclassCall_DoesNotOfferCustomTypeIfAlreadyCustom() {
        myFixture.configureByText(
            "a.py",
            """
            import dataclasses


            class ProductId(int):
                pass


            @dataclasses.dataclass
            class D:
                product_id: ProductId


            def do():
                D(product_id=12<caret>3)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from int")
        assertEmpty("Should not offer introducing a custom type when one already exists", intentions)
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

        myFixture.checkResult(
            """
            import dataclasses
            
            
            class ProductId(int):
                pass
            
            
            @dataclasses.dataclass
            class D:
                product_id: ProductId
                """.trimIndent()
        )
    }

    fun testStringLiteral_NoAnnotation_WrapsWithCustomStr() {
        myFixture.configureByText(
            "a.py",
            """
            def expect_str(s) -> None:
                ...

            expect_str("ab<caret>c")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customstr(str):
                pass


            def expect_str(s) -> None:
                ...

            expect_str(Customstr("abc"))
            """.trimIndent()
        )
    }

    fun testStringLiteral_Annotation_UpdatesAnnotationAfterWrap() {
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

        myFixture.checkResult(
            """
            class Customstr(str):
                pass


            def expect_str(s: Customstr) -> None:
                ...

            expect_str(Customstr("abc"))
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            class Customstr(str):
                pass


            val = Customstr("str")
            """.trimIndent()
        )
    }

    // Note: module-level presence of a custom subclass should not suppress the intention.

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

        myFixture.checkResult(
            """
            class Customfloat(float):
                pass
            
            
            val = Customfloat(4567.6)
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            class ProductId(int):
                pass


            product_id = ProductId(1234)
            """.trimIndent()
        )
    }

    fun testKeywordArgument_WithSnakeCaseName_UsesKeywordForClass() {
        myFixture.configureByText(
            "a.py",
            """
            def do(my_arg) -> None:
                ...

            def test_():
                do(my_arg=12<caret>34)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class MyArg(int):
                pass


            def do(my_arg) -> None:
                ...

            def test_():
                do(my_arg=MyArg(1234))
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            import pydantic


            class ProductId(int):
                pass
            
            
            class D(pydantic.BaseModel):
                product_id: ProductId
            
            
            def do():
                D(product_id=ProductId(123))
                D(product_id=ProductId(456))
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            from pydantic import BaseModel


            class ProductId(int):
                pass
            
            
            class D(BaseModel):
                product_id: ProductId
            
            
            def do():
                D(product_id=ProductId(123))
                D(product_id=ProductId(456))
            """.trimIndent()
        )
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

        myFixture.checkResult(
            """
            from pydantic import BaseModel


            class ProductId(int):
                pass
            
            
            class D(BaseModel):
                product_id: ProductId
                other: str
            
            
            def do():
                D(ProductId(123), "a")
                D(ProductId(456), "b")
            """.trimIndent()
        )
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
        assertTrue(modelText.contains("class ProductId(int):"))
        assertTrue(modelText.contains("product_id: ProductId"))

        // Usage site should import the new custom type and use it at the call-site.
        assertTrue(
            usageText.contains("from model import D, ProductId") ||
                    (usageText.contains("from model import D") && usageText.contains("from model import ProductId"))
        )
        assertTrue(usageText.contains("D(product_id=ProductId(123))"))
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
        assertTrue(modelText.contains("class ProductId(int):"))
        assertFalse(usageText.contains("class ProductId(int):"))
    }
}
