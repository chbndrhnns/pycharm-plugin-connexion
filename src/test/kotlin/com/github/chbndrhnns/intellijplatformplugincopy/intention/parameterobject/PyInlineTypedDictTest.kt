package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doIntentionTest

class PyInlineTypedDictTest : TestBase() {

    fun testInlineTypedDict() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import TypedDict

            class Point(TypedDict):
                x: int
                y: int

            def dis<caret>t(p: Point):
                print(p["x"] + p["y"])

            def main():
                dist(Point(x=1, y=2))
            """.trimIndent(),
            """
            from typing import TypedDict

            class Point(TypedDict):
                x: int
                y: int

            def dist(x: int, y: int):
                print(x + y)

            def main():
                dist(x=1, y=2)
            """.trimIndent(),
            "BetterPy: Inline parameter object",
        )
    }
}
