package com.github.chbndrhnns.betterpy.features.intentions.wrap

import fixtures.TestBase
import fixtures.doIntentionTest

class WrapContextBreadthTest : TestBase() {

    fun testYield_IntToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Iterator
            def gen() -> Iterator[str]:
                val_int = 1
                yield <caret>val_int
            """,
            """
            from typing import Iterator
            def gen() -> Iterator[str]:
                val_int = 1
                yield str(val_int)
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun testDefaultValue_IntToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def foo(x: str = <caret>1):
                pass
            """,
            """
            def foo(x: str = "1"):
                pass
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun testLambdaBody_IntToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Callable
            val_int = 1
            f: Callable[[], str] = lambda: <caret>val_int
            """,
            """
            from typing import Callable
            val_int = 1
            f: Callable[[], str] = lambda: str(val_int)
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun testListComprehension_IntToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import List
            l: List[str] = [<caret>x for x in range(3)]
            """,
            """
            from typing import List
            l: List[str] = [str(x) for x in range(3)]
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun testPatternMatchingBranch_IntToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def f(x):
                val_int = 1
                match x:
                    case 1:
                        y: str = <caret>val_int
            """,
            """
            def f(x):
                val_int = 1
                match x:
                    case 1:
                        y: str = str(val_int)
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun testWalrusOperator_IntToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def f(p: str): ...
            if (x := 1):
               f(<caret>x)
            """,
            """
            def f(p: str): ...
            if (x := 1):
               f(str(x))
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun testAttributeChain_IntToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class A:
                x: int = 1
            a = A()
            y: str = a.<caret>x
            """,
            """
            class A:
                x: int = 1
            a = A()
            y: str = str(a.x)
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun testMethodChain_IntToStr_WrapsWithStrConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class B:
                def foo(self) -> int: return 1
            b = B()
            y: str = b.foo<caret>()
            """,
            """
            class B:
                def foo(self) -> int: return 1
            b = B()
            y: str = str(b.foo())
            """,
            "BetterPy: Wrap with str()"
        )
    }

}
