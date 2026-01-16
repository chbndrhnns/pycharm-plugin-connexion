package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyInlineParameterObjectVariableTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.InlineParameterObjectRefactoringAction"

    fun testInlineWithVariableArgument() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any

            @dataclass(frozen=True, slots=True, kw_only=True)
            class MyParams:
                a: int
                b: int

            def func(<caret>params: MyParams):
                print(params.a, params.b)

            def main():
                params = MyParams(a=1, b=2)
                func(params)
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any

            @dataclass(frozen=True, slots=True, kw_only=True)
            class MyParams:
                a: int
                b: int

            def func(a: int, b: int):
                print(a, b)

            def main():
                params = MyParams(a=1, b=2)
                func(a=1, b=2)
            """.trimIndent(),
            actionId
        )
    }
}
