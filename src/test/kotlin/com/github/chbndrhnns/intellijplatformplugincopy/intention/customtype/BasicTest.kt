package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionNotAvailable
import fixtures.doRefactoringActionTest

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

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.IntroduceCustomTypeRefactoringAction"

    fun testSimpleAnnotatedAssignment_Int_RewritesAnnotation() {
        myFixture.doRefactoringActionTest(
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
            actionId,
            renameTo = "Customint"
        )
    }

    fun testFinalConstant_UsesPascalCaseName() {
        myFixture.doRefactoringActionTest(
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
            actionId
        )
    }


    fun testSimpleAnnotatedParam_Int_RewritesAnnotation() {
        myFixture.doRefactoringActionTest(
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
            actionId,
            renameTo = "Customint"
        )
    }

    fun testSimpleDataclassField_UsesCustomType() {
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

    fun testSimplePydanticField_UsesCustomType() {
        myFixture.doRefactoringActionTest(
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
            actionId
        )
    }

    fun testIntentionNotAvailable_WhenAlreadyCustomTypeInheritingStr() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            class CustomStr(str):
                pass

            def do():
                val: CustomStr = "a<caret>bc"
            """,
            actionId
        )
    }

    fun testIntentionNotAvailable_OnFunctionCallResult() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            def do():
                return 1


            def usage():
                val = <caret>do()
            """,
            actionId
        )
    }

    fun testIntentionNotAvailable_OnAssignmentTargetOfFunctionCallResult() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            def do():
                return 1


            def usage():
                va<caret>l = do()
            """,
            actionId
        )
    }

    fun testIntentionAvailableForInFunctionCall() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            val = dict({"<caret>a": 1, "b": 2, "c": 3})
            """,
            """
            class Customstr(str):
                __slots__ = ()
            
            
            val = dict({Customstr("a"): 1, "b": 2, "c": 3})
            """,
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testCallSiteKeywordArgument_AddsParameterAnnotation() {
        myFixture.doRefactoringActionTest(
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
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testAssignmentVariableRewrite_WhenCaretOnVariable() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            a<caret>bc: str = "text"
            """,
            """
            class Customstr(str):
                __slots__ = ()
            
            
            abc: Customstr = Customstr("text")
            """,
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testFString_UpgradesReferencedVariable_InsteadOfWrappingContent() {
        myFixture.doRefactoringActionTest(
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
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testFString_UpgradesReferencedParameter_InsteadOfWrappingContent() {
        myFixture.doRefactoringActionTest(
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
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testIntentionNotAvailable_InLoopVariable() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            for it<caret>em in [1, 2, 3]:
                pass
            """,
            actionId
        )
    }

    fun testIntentionNotAvailable_InIsInstanceCheck() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            def f(x):
                return isinstance(x, i<caret>nt)
            """,
            actionId
        )
    }

    fun testIntentionNotAvailable_WhenFileHasParseError() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            def broken(:
                pass

            val: i<caret>nt = 123
            """,
            actionId
        )
    }
}
