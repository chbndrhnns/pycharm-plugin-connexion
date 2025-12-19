package com.github.chbndrhnns.intellijplatformplugincopy.intention.unwrap

import fixtures.TestBase
import fixtures.doIntentionTest

class UnwrapContextBreadthTest : TestBase() {

    fun testYield_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType, Iterator
            UserId = NewType("UserId", int)
            
            def f() -> Iterator[int]:
                yield <caret>UserId(1)
            """,
            """
            from typing import NewType, Iterator
            UserId = NewType("UserId", int)
            
            def f() -> Iterator[int]:
                yield 1
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testDefaultValue_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x: int = <caret>UserId(1)):
                pass
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x: int = 1):
                pass
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testKeywordArgument_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x: int): pass
            
            f(x=<caret>UserId(1))
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x: int): pass
            
            f(x=1)
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testLambdaBody_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType, Callable
            UserId = NewType("UserId", int)
            
            f: Callable[[], int] = lambda: <caret>UserId(1)
            """,
            """
            from typing import NewType, Callable
            UserId = NewType("UserId", int)
            
            f: Callable[[], int] = lambda: 1
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testComprehension_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType, List
            UserId = NewType("UserId", int)
            
            l: List[int] = [<caret>UserId(x) for x in range(3)]
            """,
            """
            from typing import NewType, List
            UserId = NewType("UserId", int)
            
            l: List[int] = [x for x in range(3)]
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testPatternMatching_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x):
                match x:
                    case 1:
                        y: int = <caret>UserId(1)
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x):
                match x:
                    case 1:
                        y: int = 1
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testWalrusOperator_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(p: int): ...
            if (x := <caret>UserId(1)):
               f(x)
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(p: int): ...
            if (x := 1):
               f(x)
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testAttributeChain_Unwraps() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            class A:
                x: int = 1
            a = A()
            y: int = <caret>UserId(a.x)
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            class A:
                x: int = 1
            a = A()
            y: int = a.x
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

}
