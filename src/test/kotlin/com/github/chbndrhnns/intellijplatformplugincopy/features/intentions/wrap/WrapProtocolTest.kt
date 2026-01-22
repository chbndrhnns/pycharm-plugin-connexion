package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class WrapProtocolTest : TestBase() {
    fun testProtocol_NoWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import Protocol

            class MyProtocol(Protocol):
                def do_something(self) -> None: ...

            def foo(p: MyProtocol):
                pass

            foo(<caret>42)
            """,
            "BetterPy: Wrap with MyProtocol()"
        )
    }

    fun testProtocol_InUnion_NoWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import Protocol, Union

            class MyProtocol(Protocol):
                def do_something(self) -> None: ...
            
            class MyReal:
                def __init__(self, x: int): pass

            def foo(p: Union[MyProtocol, MyReal]):
                pass

            foo(<caret>42)
            """,
            "BetterPy: Wrap with MyProtocol()"
        )
    }

    fun testProtocol_InContainer_NoWrapOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import Protocol

            class MyProtocol(Protocol):
                def do_something(self) -> None: ...

            def foo(p: list[MyProtocol]):
                pass

            foo([<caret>42])
            """,
            "BetterPy: Wrap with MyProtocol()"
        )
    }
}
