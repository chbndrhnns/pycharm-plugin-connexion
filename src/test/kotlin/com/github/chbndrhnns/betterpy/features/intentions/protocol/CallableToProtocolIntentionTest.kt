package com.github.chbndrhnns.betterpy.features.intentions.protocol

import fixtures.TestBase

class CallableToProtocolIntentionTest : TestBase() {

    fun testSimpleCallable() {
        myFixture.configureByText(
            "a.py", """
            from typing import Callable
            
            def foo(c: Call<caret>able[[int, str], bool]):
                pass
        """.trimIndent()
        )

        myFixture.launchAction(CallableToProtocolIntention())

        myFixture.checkResult(
            """
            from typing import Callable, Protocol


            class MyProtocol(Protocol):
                def __call__(self, arg0: int, arg1: str) -> bool: ...


            def foo(c: MyProtocol):
                pass
        """.trimIndent() + "\n"
        )
    }

    fun testNoArgsCallable() {
        myFixture.configureByText(
            "a.py", """
            from typing import Callable
            
            x: Call<caret>able[[], str]
        """.trimIndent()
        )

        myFixture.launchAction(CallableToProtocolIntention())

        myFixture.checkResult(
            """
            from typing import Callable, Protocol


            class MyProtocol(Protocol):
                def __call__(self) -> str: ...


            x: MyProtocol
        """.trimIndent() + "\n"
        )
    }

    fun testEllipsisCallable() {
        myFixture.configureByText(
            "a.py", """
            from typing import Callable
            
            x: Call<caret>able[..., int]
        """.trimIndent()
        )

        myFixture.launchAction(CallableToProtocolIntention())

        myFixture.checkResult(
            """
            from typing import Callable, Protocol, Any


            class MyProtocol(Protocol):
                def __call__(self, *args: Any, **kwargs: Any) -> int: ...


            x: MyProtocol
        """.trimIndent() + "\n"
        )
    }

    fun testQualifiedCallable() {
        myFixture.configureByText(
            "a.py", """
            import typing
            
            x: typing.Call<caret>able[[int], str]
        """.trimIndent()
        )

        myFixture.launchAction(CallableToProtocolIntention())

        myFixture.checkResult(
            """
            import typing
            from typing import Protocol


            class MyProtocol(Protocol):
                def __call__(self, arg0: int) -> str: ...


            x: MyProtocol
        """.trimIndent() + "\n"
        )
    }

    fun testNameCollision() {
        myFixture.configureByText(
            "a.py", """
            from typing import Callable, Protocol
            
            class MyProtocol(Protocol): ...
            
            x: Call<caret>able[[int], str]
        """.trimIndent()
        )

        myFixture.launchAction(CallableToProtocolIntention())

        myFixture.checkResult(
            """
            from typing import Callable, Protocol


            class MyProtocol1(Protocol):
                def __call__(self, arg0: int) -> str: ...


            class MyProtocol(Protocol): ...


            x: MyProtocol1
        """.trimIndent() + "\n"
        )
    }

    fun testModuleLevelVariable() {
        myFixture.configureByText(
            "a.py", """
            from typing import Callable
            
            # Some comment
            my_var: Call<caret>able[[int], str]
        """.trimIndent()
        )

        myFixture.launchAction(CallableToProtocolIntention())

        myFixture.checkResult(
            """
            from typing import Callable, Protocol


            class MyProtocol(Protocol):
                def __call__(self, arg0: int) -> str: ...


            # Some comment
            my_var: MyProtocol
        """.trimIndent() + "\n"
        )
    }
}
