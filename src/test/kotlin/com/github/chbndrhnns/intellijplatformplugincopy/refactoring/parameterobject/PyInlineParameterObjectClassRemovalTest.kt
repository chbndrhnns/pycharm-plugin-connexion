package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyInlineParameterObjectClassRemovalTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.InlineParameterObjectRefactoringAction"

    fun testInlineRemoveClassAfterAllUsagesRemoved() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any

            @dataclass(frozen=True, slots=True, kw_only=True)
            class UserParams:
                name: Any
                age: Any

            def create_user(<caret>params: UserParams):
                print(params.name, params.age)

            def main():
                create_user(UserParams(name="John", age=30))
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any


            def create_user(name: Any, age: Any):
                print(name, age)

            def main():
                create_user(name="John", age=30)
            """.trimIndent(),
            actionId
        )

        // Verify class is gone
        // The doRefactoringTest checks the text match, so if the class is missing in 'after', it implies it was removed.
        // But let's be explicit about ensuring it IS removed in the 'after' text.
        // The 'after' text above does NOT contain the class definition.
    }

    fun testInlineDoesNotRemoveClassIfUsagesRemain() {
        // Here we inline one usage, but another remains (e.g. another function not being inlined, 
        // effectively simulating 'inline all = false' or just having a usage that wasn't covered)

        // However, the current action defaults to 'inline all'. 
        // We can simulate a usage that is NOT a parameter type of a function we are inlining.
        // E.g. a variable annotation.

        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any

            @dataclass(frozen=True, slots=True, kw_only=True)
            class UserParams:
                name: Any
                age: Any

            def create_user(<caret>params: UserParams):
                print(params.name, params.age)

            def other_usage():
                p: UserParams = UserParams(name="Jane", age=25)
                print(p)
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any

            @dataclass(frozen=True, slots=True, kw_only=True)
            class UserParams:
                name: Any
                age: Any

            def create_user(name: Any, age: Any):
                print(name, age)

            def other_usage():
                p: UserParams = UserParams(name="Jane", age=25)
                print(p)
            """.trimIndent(),
            actionId
        )
    }
}
