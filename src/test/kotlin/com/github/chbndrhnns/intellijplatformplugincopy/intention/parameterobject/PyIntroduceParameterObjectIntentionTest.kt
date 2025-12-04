package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doIntentionTest

class PyIntroduceParameterObjectIntentionTest : TestBase() {

    fun testSimpleIntroduceParameterObject() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def create_<caret>user(first_name, last_name, email, age):
                print(first_name, last_name, email, age)
            
            
            def main():
                create_user("John", "Doe", "john@example.com", 30)
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any
            
            
            @dataclass
            class CreateUserParams:
                first_name: Any
                last_name: Any
                email: Any
                age: Any
            
            
            def create_user(params: CreateUserParams):
                print(params.first_name, params.last_name, params.email, params.age)
            
            
            def main():
                create_user(CreateUserParams(first_name="John", last_name="Doe", email="john@example.com", age=30))
            """.trimIndent() + "\n\n",
            "Introduce parameter object"
        )
    }

    fun testMethodIntroduceParameterObject() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class Class:
                def do(self, <caret>arg1, arg2):
                    return {}
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any
            
            
            @dataclass
            class DoParams:
                arg1: Any
                arg2: Any
            
            
            class Class:
                def do(self, params: DoParams):
                    return {}""".trimIndent() + "\n\n",
            "Introduce parameter object"
        )
    }
}
