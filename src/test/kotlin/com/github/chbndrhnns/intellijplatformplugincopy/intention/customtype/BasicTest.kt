package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

/**
 * Basic smoke tests for introducing a custom type from stdlib types.
 *
 * These are intentionally small, "golden path" scenarios. More nuanced and
 * variant-heavy cases live in the thematic suites:
 * - [DataclassTest]
 * - [PydanticTest]
 * - [LiteralsTest]
 * - [HeavyTest]
 */
class BasicTest : TestBase() {

    fun testSimpleAnnotatedAssignment_Int_RewritesAnnotation() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def test_():
                val: int = 1<caret>234
            """,
            """
            class Customint(int):
                pass


            def test_():
                val: Customint = Customint(1234)
            """,
            "BetterPy: Introduce custom type from int",
            renameTo = "Customint"
        )
    }

    fun testFinalConstant_UsesPascalCaseName() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Final


            MY_CONSTANT: Final[str] = "VAL<caret>UE"
            """,
            """
            from typing import Final


            class MyConstant(str):
                __slots__ = ()


            MY_CONSTANT: Final[MyConstant] = MyConstant("VALUE")
            """,
            "BetterPy: Introduce custom type from str"
        )
    }


    fun testSimpleAnnotatedParam_Int_RewritesAnnotation() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def f(x: <caret>int) -> None:
                ...
            """,
            """
            class Customint(int):
                pass


            def f(x: Customint) -> None:
                ...
            """,
            "BetterPy: Introduce custom type from int",
            renameTo = "Customint"
        )
    }

    fun testSimpleDataclassField_UsesCustomType() {
        myFixture.doIntentionTest(
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
            "BetterPy: Introduce custom type from int"
        )
    }

    fun testSimplePydanticField_UsesCustomType() {
        myFixture.doIntentionTest(
            "a.py",
            """
            import pydantic


            class D(pydantic.BaseModel):
                product_id: i<caret>nt


            def do():
                D(product_id=123)
                D(product_id=456)
            """,
            """
            import pydantic


            class ProductId(int):
                pass
            
            
            class D(pydantic.BaseModel):
                product_id: ProductId
            
            
            def do():
                D(product_id=ProductId(123))
                D(product_id=ProductId(456))
            """,
            "BetterPy: Introduce custom type from int"
        )
    }

    fun testIntentionNotAvailable_WhenAlreadyCustomTypeInheritingStr() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class CustomStr(str):
                pass

            def do():
                val: CustomStr = "a<caret>bc"
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionNotAvailable_WhenTypeErrorPresent() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import NewType
            
            One = NewType("One", str)
            
            val: One = "a<caret>bc"
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionNotAvailable_WhenTypeErrorOnRhsOfAnnotatedAssignment() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def do():
                return {}


            val: i<caret>nt = do()
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionNotAvailable_OnFunctionCallResult() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def do():
                return 1


            def usage():
                val = <caret>do()
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionNotAvailable_OnAssignmentTargetOfFunctionCallResult() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def do():
                return 1


            def usage():
                va<caret>l = do()
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionAvailableForInFunctionCall() {
        myFixture.doIntentionTest(
            "a.py",
            """
            val = dict({"<caret>a": 1, "b": 2, "c": 3})
            """,
            """
            class Customstr(str):
                __slots__ = ()
            
            
            val = dict({Customstr("a"): 1, "b": 2, "c": 3})
            """,
            "BetterPy: Introduce custom type from str",
            renameTo = "Customstr"
        )
    }

    fun testCallSiteKeywordArgument_AddsParameterAnnotation() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(*, arg1, arg2):
                return arg1 + arg2


            do(arg1="a<caret>", arg2="b")
            """,
            """
            class Customstr(str):
                __slots__ = ()


            def do(*, arg1: Customstr, arg2):
                return arg1 + arg2


            do(arg1=Customstr("a"), arg2="b")
            """,
            "BetterPy: Introduce custom type from str",
            renameTo = "Customstr"
        )
    }

    fun testAssignmentVariableRewrite_WhenCaretOnVariable() {
        myFixture.doIntentionTest(
            "a.py",
            """
            a<caret>bc: str = "text"
            """,
            """
            class Customstr(str):
                __slots__ = ()
            
            
            abc: Customstr = Customstr("text")
            """,
            "BetterPy: Introduce custom type from str",
            renameTo = "Customstr"
        )
    }

    fun testFString_UpgradesReferencedVariable_InsteadOfWrappingContent() {
        myFixture.doIntentionTest(
            "a.py",
            """
            abc: str = "text"
            s = f"{a<caret>bc}"
            """,
            """
            class Customstr(str):
                __slots__ = ()
            
            
            abc: Customstr = Customstr("text")
            s = f"{abc}"
            """,
            "BetterPy: Introduce custom type from str",
            renameTo = "Customstr"
        )
    }

    fun testFString_UpgradesReferencedParameter_InsteadOfWrappingContent() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(a: str):
                return f"{<caret>a}"
            """,
            """
            class Customstr(str):
                __slots__ = ()


            def do(a: Customstr):
                return f"{a}"
            """,
            "BetterPy: Introduce custom type from str",
            renameTo = "Customstr"
        )
    }

    fun testIntentionNotAvailable_InLoopVariable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            for it<caret>em in [1, 2, 3]:
                pass
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionNotAvailable_InIsInstanceCheck() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(x):
                return isinstance(x, i<caret>nt)
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionNotAvailable_WhenFileHasParseError() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def broken(:
                pass

            val: i<caret>nt = 123
            """,
            "Introduce custom type"
        )
    }
}
