package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.populate

import fixtures.TestBase
import fixtures.doIntentionTest
import fixtures.withPopulatePopupSelection

class IgnoreUnderscoreParametersTest : TestBase() {

    fun testIgnoreUnderscoreParameters() {
        withPopulatePopupSelection(index = 0) { // All arguments, non-recursive
            myFixture.doIntentionTest(
                "a.py",
                """
                def foo(a, _b, c, _d):
                    pass

                foo(<caret>)
                """,
                """
                def foo(a, _b, c, _d):
                    pass

                foo(a=..., c=...)
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testIgnoreUnderscoreParametersInDataclass() {
        withPopulatePopupSelection(index = 0) { // All arguments, non-recursive
            myFixture.doIntentionTest(
                "a.py",
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    x: int
                    _y: int
                    z: int = 1
                    _w: int = 2

                a = A(<caret>)
                """,
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    x: int
                    _y: int
                    z: int = 1
                    _w: int = 2

                a = A(x=..., z=...)
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }
}
