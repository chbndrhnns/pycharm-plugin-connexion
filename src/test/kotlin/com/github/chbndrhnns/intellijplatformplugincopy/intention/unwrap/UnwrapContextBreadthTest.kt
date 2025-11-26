package com.github.chbndrhnns.intellijplatformplugincopy.intention.unwrap

import fixtures.TestBase

class UnwrapContextBreadthTest : TestBase() {

    fun testYield_Unwraps() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType, Iterator
            UserId = NewType("UserId", int)
            
            def f() -> Iterator[int]:
                yield <caret>UserId(1)
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import NewType, Iterator
            UserId = NewType("UserId", int)
            
            def f() -> Iterator[int]:
                yield 1
            """.trimIndent()
        )
    }

    fun testDefaultValue_Unwraps() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x: int = <caret>UserId(1)):
                pass
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x: int = 1):
                pass
            """.trimIndent()
        )
    }

    fun testKeywordArgument_Unwraps() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x: int): pass
            
            f(x=<caret>UserId(1))
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x: int): pass
            
            f(x=1)
            """.trimIndent()
        )
    }

    fun testLambdaBody_Unwraps() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType, Callable
            UserId = NewType("UserId", int)
            
            f: Callable[[], int] = lambda: <caret>UserId(1)
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import NewType, Callable
            UserId = NewType("UserId", int)
            
            f: Callable[[], int] = lambda: 1
            """.trimIndent()
        )
    }

    fun testComprehension_Unwraps() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType, List
            UserId = NewType("UserId", int)
            
            l: List[int] = [<caret>UserId(x) for x in range(3)]
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import NewType, List
            UserId = NewType("UserId", int)
            
            l: List[int] = [x for x in range(3)]
            """.trimIndent()
        )
    }

    fun testPatternMatching_Unwraps() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x):
                match x:
                    case 1:
                        y: int = <caret>UserId(1)
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(x):
                match x:
                    case 1:
                        y: int = 1
            """.trimIndent()
        )
    }

    fun testWalrusOperator_Unwraps() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(p: int): ...
            if (x := <caret>UserId(1)):
               f(x)
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            def f(p: int): ...
            if (x := 1):
               f(x)
            """.trimIndent()
        )
    }

    fun testAttributeChain_Unwraps() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            class A:
                x: int = 1
            a = A()
            y: int = <caret>UserId(a.x)
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            
            class A:
                x: int = 1
            a = A()
            y: int = a.x
            """.trimIndent()
        )
    }

}
