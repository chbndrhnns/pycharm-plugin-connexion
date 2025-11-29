package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.doIntentionTest

class PydanticTest : TestBase() {
    fun testCall_IntroduceFromKeywordValue_UpdatesFieldAndUsages() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from pydantic import BaseModel


            class D(BaseModel):
                product_id: int


            def do():
                D(product_id=12<caret>3)
                D(product_id=456)
            """,
            """
            from pydantic import BaseModel
            
            
            class ProductId(int):
                pass
            
            
            class D(BaseModel):
                product_id: ProductId
            
            
            def do():
                D(product_id=ProductId(123))
                D(product_id=ProductId(456))
            """,
            "Introduce custom type from int"
        )
    }
}
