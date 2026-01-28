package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectTypedDictTest : TestBase() {

    private val actionId = INTRODUCE_PARAMETER_OBJECT_ACTION_ID

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
                from typing import TypedDict, NotRequired
                
                
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
