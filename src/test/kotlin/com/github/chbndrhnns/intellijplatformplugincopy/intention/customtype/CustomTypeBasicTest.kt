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

    fun testSimpleAnnotatedAssignment_Int_RewritesAnnotation() {
        myFixture.configureByText(
            "a.py",
            """
            def test_():
                val: int = 1<caret>234
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customint(int):
                pass


            def test_():
                val: Customint = Customint(1234)
            """.trimIndent()
        )
    }


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

    fun testIntentionNotAvailable_WhenAlreadyCustomTypeInheritingStr() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomStr(str):
                pass

            def do():
                val: CustomStr = "a<caret>bc"
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type")
        assertEmpty("Intention should not be available when type is already a custom subclass of str", intentions)
    }

    fun testIntentionNotAvailable_WhenTypeErrorPresent() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            
            One = NewType("One", str)
            
            val: One = "a<caret>bc"
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type")
        assertEmpty("Intention should not be available when type error is present", intentions)
    }

    fun testIntentionAvailableForInFunctionCall() {
        myFixture.configureByText(
            "a.py",
            """
            val = dict({"<caret>a": 1, "b": 2, "c": 3})
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customstr(str):
                pass
            
            
            val = dict({Customstr("a"): 1, "b": 2, "c": 3})
            """.trimIndent()
        )
    }

    fun testAssignmentVariableRewrite_WhenCaretOnVariable() {
        myFixture.configureByText(
            "a.py", """
            a<caret>bc: str = "text"
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        val text = myFixture.editor.document.text
        assertTrue(text.contains("class Customstr(str):"))
        assertTrue(text.contains("abc: Customstr = Customstr(\"text\")"))
        assertFalse(text.contains("Customstr(abc)"))
    }
}
