package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyInlineParameterObjectRefactoringTest : TestBase() {

    private val actionId = "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.InlineParameterObjectRefactoringAction"

    fun testSimpleInlineParameterObject() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class CreateUserParams:
                first_name: Any
                last_name: Any
                email: Any
                age: Any


            def create_<caret>user(params: CreateUserParams):
                print(params.first_name, params.last_name, params.email, params.age)


            def main():
                create_user(CreateUserParams(first_name="John", last_name="Doe", email="john@example.com", age=30))
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class CreateUserParams:
                first_name: Any
                last_name: Any
                email: Any
                age: Any


            def create_user(first_name: Any, last_name: Any, email: Any, age: Any):
                print(first_name, last_name, email, age)


            def main():
                create_user(first_name="John", last_name="Doe", email="john@example.com", age=30)
            """.trimIndent(),
            actionId
        )
    }
}
