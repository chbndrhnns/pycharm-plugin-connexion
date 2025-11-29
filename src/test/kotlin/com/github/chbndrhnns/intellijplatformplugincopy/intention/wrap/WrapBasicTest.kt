package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

/**
 * Basic wrap intention behavior: assignments, arguments, returns without
 * collections, unions, forward refs or dataclasses.
 */
class WrapBasicTest : TestBase() {

    fun testAssignment_PathToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from pathlib import Path
            a: str = Path(<caret>"val")
            """,
            """
            from pathlib import Path
            a: str = str(Path("val"))
            """,
            "Wrap with str()"
        )
    }

    fun testCustomInt_UnwrapAvailable_WrapNotOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[int] = [
            CustomInt(<caret>1),
            ] 
            """,
            "Wrap with"
        )
    }

    fun testAssignment_StrToPath_WrapsWithPathConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from pathlib import Path
            a: Path = "<caret>val"
            """,
            """
            from pathlib import Path
            a: Path = Path("val")
            """,
            "Wrap with Path()"
        )
    }

    fun testReturn_FloatToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(val: float) -> str:
                return <caret>val
            """,
            """
            def do(val: float) -> str:
                return str(val)
            """,
            "Wrap with str()"
        )
    }

    fun testCall_NoExpectedType_NoStrWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def process_data() -> int:
                return 1            
            
            result = process_data<caret>()
            """,
            "Wrap with str()"
        )
    }

    fun testArgument_IntLiteral_ReplacedWithStrLiteral() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def process_data(arg: str) -> int:
                return int(1)
            
            result = process_data(1<caret>23)
            """,
            """
            def process_data(arg: str) -> int:
                return int(1)
            
            result = process_data("123")
            """,
            "Wrap with str()"
        )
    }

    fun testKeywordOnlyParam_IntToBool_WrapsWithBoolConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class CompletedProcess: ...
            
            def run(*, shell: bool = False) -> CompletedProcess:
                return CompletedProcess()
            
            run(shell=<caret>0)
            """,
            """
            class CompletedProcess: ...
            
            def run(*, shell: bool = False) -> CompletedProcess:
                return CompletedProcess()
            
            run(shell=bool(0))
            """,
            "Wrap with bool()"
        )
    }

    fun testClassInit_ParseKwArg_NoWrapSuggestion() {
        // This tests that we do not fall back on class variables when suggesting wraps
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class Client:
                version: int
            
                def __init__(self, val: str) -> None:
                    self.val = val
            
            
            Client(val="a<caret>bc")
            """,
            "Wrap with"
        )
    }

    fun testPrint_StrArgument_NoObjectWrapSuggestion() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            print("a<caret>bc")
            """,
            "Wrap with object()"
        )
    }

    fun testArgument_AnyExpected_NoAnyWrapSuggestion() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import Any
            
            def foo(x: Any):
                pass
            
            foo("s<caret>tr")
            """,
            "Wrap with Any()"
        )
    }

    fun testExistingMatch_NoWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import TypeVar, Generic, Union
            
            T = TypeVar("T")
            class PathLike(Generic[T]): ...
            
            class Path(PathLike[str]): ...
            
            def open(file: Union[str, bytes, PathLike[str], PathLike[bytes], int]): ...
            
            val = Path()
            open(v<caret>al)
            """,
            "Wrap with"
        )
    }
}
