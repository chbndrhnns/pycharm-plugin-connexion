package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

/**
 * Basic smoke tests for introducing a custom type from stdlib types.
 *
 * These are intentionally small, "golden path" scenarios. More nuanced and
 * variant-heavy cases live in the thematic suites:
 * - [CustomTypeDataclassTest]
 * - [CustomTypePydanticTest]
 * - [CustomTypeLiteralsTest]
 * - [CustomTypeHeavyTest]
 */
class CustomTypeBasicTest : TestBase() {

    fun testSimpleAnnotatedParam_Int_RewritesAnnotation() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: <caret>int) -> None:
                ...
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customint(int):
                pass


            def f(x: Customint) -> None:
                ...
            """.trimIndent()
        )
    }

    fun testSimpleDataclassField_UsesCustomType() {
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

    fun testSimplePydanticField_UsesCustomType() {
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
}
