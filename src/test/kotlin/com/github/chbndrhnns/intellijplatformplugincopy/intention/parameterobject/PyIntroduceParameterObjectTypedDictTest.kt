package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectTypedDictTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.IntroduceParameterObjectRefactoringAction"

    fun testIntroduceTypedDictWithOptionalFields() {
        withMockIntroduceParameterObjectDialog({ dialog ->
            dialog.setBaseType(ParameterObjectBaseType.TYPED_DICT)
        }) {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def create_user(name: str, age: int = 18):
                    print(name, age)

                def main():
                    create_<caret>user("John")
                """.trimIndent(),
                """
                from typing import TypedDict, Any, NotRequired
                
                
                class CreateUserParams(TypedDict):
                    name: str
                    age: NotRequired[int]
                
                
                def create_user(params: CreateUserParams):
                    print(params["name"], params.get("age", 18))
                
                
                def main():
                    create_user(CreateUserParams(name="John"))
                """.trimIndent(),
                actionId
            )
        }
    }
}
