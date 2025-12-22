package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doIntentionTest

class PyInlineNamedTupleTest : TestBase() {

    fun testInlineNamedTuple() {
        myFixture.doIntentionTest(
            "a.py",
            """
        from typing import NamedTuple

        class Point(NamedTuple):
            x: int
            y: int

        def dis<caret>t(p: Point):
            print(p.x + p.y)

        def main():
            dist(Point(1, 2))
        """.trimIndent(),
            """
        from typing import NamedTuple

        class Point(NamedTuple):
            x: int
            y: int

        def dist(x: int, y: int):
            print(x + y)

        def main():
            dist(1, 2)
            """.trimIndent(),
            "BetterPy: Inline parameter object",
        )
    }
}
