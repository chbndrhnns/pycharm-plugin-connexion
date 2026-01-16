package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectNameCollisionTest : TestBase() {

    fun testCollisionWithLocalClass() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
    
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class CreateUserParams1:
                    first_name: Any
                    last_name: Any
                
                
                def create_user(params: CreateUserParams1):
                    print(params.first_name, params.last_name)
                """.trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }

    fun testCollisionWithImportedClass() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class CreateUserParams1:
                    first_name: Any
                    last_name: Any
                
                
                def create_user(params: CreateUserParams1):
                    print(params.first_name, params.last_name)
                """.trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }
}
