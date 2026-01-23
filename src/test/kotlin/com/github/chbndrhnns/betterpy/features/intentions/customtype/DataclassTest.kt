package com.github.chbndrhnns.betterpy.features.intentions.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionNotAvailable
import fixtures.doRefactoringActionTest

class DataclassTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testField_WrapsKeywordArgumentUsages() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: i<caret>nt


            def do():
                D(product_id=123)
                D(product_id=456)
            """,
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
            """,
            actionId
        )
    }

    fun testField_WrapsPositionalArgumentUsages() {
        myFixture.doRefactoringActionTest(
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
            """,
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
            """,
            actionId
        )
    }

    fun testCall_IntroduceFromKeywordValue_UpdatesFieldAndAllUsages() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: int


            def do():
                D(product_id=12<caret>3)
                D(product_id=456)
            """,
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
            """,
            actionId
        )
    }

    fun testCall_IntroduceFromPositionalValue_UpdatesFieldAndUsages() {
        myFixture.doRefactoringActionTest(
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
            """,
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
            """,
            actionId
        )
    }

    fun testCall_DoesNotOfferCustomTypeIfAlreadyCustom() {
        myFixture.assertRefactoringActionNotAvailable(
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
            """,
            actionId
        )
    }

    fun testField_WithSnakeCaseName_UsesFieldNameForClass() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: i<caret>nt
            """,
            """
            import dataclasses
            
            
            class ProductId(int):
                pass
            
            
            @dataclasses.dataclass
            class D:
                product_id: ProductId
            """,
            actionId
        )
    }

    fun testField_ListOfStr_UsesPluralFieldNameForClass() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class FileUpload:
                files: li<caret>st[str]
            """,
            """
            import dataclasses
            
            
            class Files(list[str]):
                pass
            
            
            @dataclasses.dataclass
            class FileUpload:
                files: Files
            """,
            actionId
        )
    }

    fun testField_UnionWithNone_DefaultNoneIsNotWrapped() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class C:
                val: i<caret>nt | None = None
            """,
            """
            import dataclasses


            class Customint(int):
                pass
            
            
            @dataclasses.dataclass
            class C:
                val: Customint | None = None
            """,
            actionId,
            renameTo = "Customint"
        )
    }
}
