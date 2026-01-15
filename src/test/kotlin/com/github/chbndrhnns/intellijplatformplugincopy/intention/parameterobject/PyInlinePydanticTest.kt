package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyInlinePydanticTest : TestBase() {

    fun testInlinePydanticModel() {
        myFixture.addFileToProject("pydantic.py", "class BaseModel: pass")
        myFixture.doRefactoringTest(
            "a.py",
            """
            from pydantic import BaseModel

            class Params(BaseModel):
                x: int
                y: int
                model_config = {}

            def fo<caret>o(p: Params):
                print(p.x)

            def main():
                foo(Params(x=1, y=2))
            """.trimIndent(),
            """
            from pydantic import BaseModel


            def foo(x: int, y: int):
                print(x)

            def main():
                foo(x=1, y=2)
            """.trimIndent(),
            "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.InlineParameterObjectRefactoringAction",
        )
    }
}
