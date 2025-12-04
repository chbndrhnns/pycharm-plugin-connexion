package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doIntentionTest

class PyIntroduceParameterObjectNameCollisionTest : TestBase() {

    fun testCollisionWithLocalClass() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                class CreateUserParams:
                    pass
                
                def create_<caret>user(first_name, last_name):
                    print(first_name, last_name)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                class CreateUserParams:
                    pass
    
                
                @dataclass(frozen=True, slots=True)
                class CreateUserParams1:
                    first_name: Any
                    last_name: Any
                
                
                def create_user(params: CreateUserParams1):
                    print(params.first_name, params.last_name)
                """.trimIndent() + "\n\n",
                "Introduce parameter object"
            )
        }
    }

    fun testCollisionWithImportedClass() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                from other import CreateUserParams
                
                def create_<caret>user(first_name, last_name):
                    print(first_name, last_name)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                from other import CreateUserParams
                
                
                @dataclass(frozen=True, slots=True)
                class CreateUserParams1:
                    first_name: Any
                    last_name: Any
                
                
                def create_user(params: CreateUserParams1):
                    print(params.first_name, params.last_name)
                """.trimIndent() + "\n\n",
                "Introduce parameter object"
            )
        }
    }
}
