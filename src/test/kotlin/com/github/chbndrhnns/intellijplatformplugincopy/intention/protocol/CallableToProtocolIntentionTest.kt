package com.github.chbndrhnns.intellijplatformplugincopy.intention.protocol

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

        java.io.File("test_output.txt").writeText(myFixture.file.text)

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
}
