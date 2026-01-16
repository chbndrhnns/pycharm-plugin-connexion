package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import fixtures.TestBase

class PyIntroduceParameterObjectCrossFileTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"

    fun testIntroduceCrossFileImports() {
        // Create the client file that uses the function
        myFixture.addFileToProject(
            "client.py",
            """
            from utils import create_user

            def main():
                create_user("John", "Doe")
            """.trimIndent()
        )

        // Configure the file where the function is defined
        myFixture.configureByText(
            "utils.py",
            """
            def create_<caret>user(first_name, last_name):
                print(first_name, last_name)
            """.trimIndent()
        )

        withMockIntroduceParameterObjectDialog {
            myFixture.performEditorAction(actionId)
        }

        // Verify utils.py (the definition file)
        myFixture.checkResult(
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class CreateUserParams:
                first_name: Any
                last_name: Any


            def create_user(params: CreateUserParams):
                print(params.first_name, params.last_name)
            """.trimIndent() + "\n"
        )

        // Verify client.py (the usage file)
        val clientFile = myFixture.findFileInTempDir("client.py")
        val clientContent = myFixture.psiManager.findFile(clientFile)!!.text

        // We expect the import to be added and the call site updated
        // Note: The import order/formatting by AddImportHelper might vary, but typically it appends or merges.
        // Since we are using standard PyCharm helpers, it should look standard.
        // We use string containment or exact match if deterministic.

        val expectedClientContent = """
            from utils import create_user, CreateUserParams


            def main():
                create_user(CreateUserParams(first_name="John", last_name="Doe"))
            """.trimIndent()

        assertEquals(expectedClientContent, clientContent)
    }
}
