package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyInlineParameterObjectBugTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.InlineParameterObjectRefactoringAction"

    fun testInlineNestedParameterObject() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams:
                arg1: int
                arg2: Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams1:
                params: FooParams


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams2:
                params: FooParams1


            async def foo(<caret>params: FooParams2):
                return params.params.params.arg1
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams:
                arg1: int
                arg2: Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams1:
                params: FooParams


            async def foo(params: FooParams1):
                return params.params.arg1
            """.trimIndent(),
            actionId
        )
    }
}
