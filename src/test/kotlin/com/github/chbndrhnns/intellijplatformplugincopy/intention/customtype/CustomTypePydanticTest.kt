package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class CustomTypePydanticTest : TestBase() {
    fun testCall_IntroduceFromKeywordValue_UpdatesFieldAndUsages() {
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

}
