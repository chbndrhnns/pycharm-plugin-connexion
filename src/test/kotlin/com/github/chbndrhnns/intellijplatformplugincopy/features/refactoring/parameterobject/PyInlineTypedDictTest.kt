package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyInlineTypedDictTest : TestBase() {

    fun testInlineTypedDict() {
        myFixture.doRefactoringTest(
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


            def dist(x: int, y: int):
                print(x + y)

            def main():
                dist(x=1, y=2)
            """.trimIndent(),
            INLINE_PARAMETER_OBJECT_ACTION_ID,
        )
    }
}
