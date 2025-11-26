package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase

class WrapContextBreadthTest : TestBase() {

    fun testYield_IntToStr_WrapsWithStrConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import Iterator
            def gen() -> Iterator[str]:
                val_int = 1
                yield <caret>val_int
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import Iterator
            def gen() -> Iterator[str]:
                val_int = 1
                yield str(val_int)
            """.trimIndent()
        )
    }

    fun testDefaultValue_IntToStr_WrapsWithStrConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            def foo(x: str = <caret>1):
                pass
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def foo(x: str = "1"):
                pass
            """.trimIndent()
        )
    }

    fun testLambdaBody_IntToStr_WrapsWithStrConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import Callable
            val_int = 1
            f: Callable[[], str] = lambda: <caret>val_int
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import Callable
            val_int = 1
            f: Callable[[], str] = lambda: str(val_int)
            """.trimIndent()
        )
    }

    fun testListComprehension_IntToStr_WrapsWithStrConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import List
            l: List[str] = [<caret>x for x in range(3)]
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import List
            l: List[str] = [str(x) for x in range(3)]
            """.trimIndent()
        )
    }

    fun testPatternMatchingBranch_IntToStr_WrapsWithStrConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x):
                val_int = 1
                match x:
                    case 1:
                        y: str = <caret>val_int
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def f(x):
                val_int = 1
                match x:
                    case 1:
                        y: str = str(val_int)
            """.trimIndent()
        )
    }

    fun testWalrusOperator_IntToStr_WrapsWithStrConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            def f(p: str): ...
            if (x := 1):
               f(<caret>x)
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def f(p: str): ...
            if (x := 1):
               f(str(x))
            """.trimIndent()
        )
    }

    fun testAttributeChain_IntToStr_WrapsWithStrConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            class A:
                x: int = 1
            a = A()
            y: str = a.<caret>x
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            class A:
                x: int = 1
            a = A()
            y: str = str(a.x)
            """.trimIndent()
        )
    }

    fun testMethodChain_IntToStr_WrapsWithStrConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            class B:
                def foo(self) -> int: return 1
            b = B()
            y: str = b.foo<caret>()
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            class B:
                def foo(self) -> int: return 1
            b = B()
            y: str = str(b.foo())
            """.trimIndent()
        )
    }

}
