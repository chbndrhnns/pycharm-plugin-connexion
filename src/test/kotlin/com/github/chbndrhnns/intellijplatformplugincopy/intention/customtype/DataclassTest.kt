package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase

class DataclassTest : TestBase() {

    fun testField_WrapsKeywordArgumentUsages() {
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

    fun testField_WrapsPositionalArgumentUsages() {
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

    fun testCall_IntroduceFromKeywordValue_UpdatesFieldAndAllUsages() {
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

    fun testCall_IntroduceFromPositionalValue_UpdatesFieldAndUsages() {
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

    fun testCall_DoesNotOfferCustomTypeIfAlreadyCustom() {
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

    fun testField_WithSnakeCaseName_UsesFieldNameForClass() {
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

    fun testField_ListOfStr_UsesPluralFieldNameForClass() {
        myFixture.configureByText(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class FileUpload:
                files: li<caret>st[str]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from list")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import dataclasses
            
            
            class Files(list[str]):
                pass
            
            
            @dataclasses.dataclass
            class FileUpload:
                files: Files
            """.trimIndent()
        )
    }
}
