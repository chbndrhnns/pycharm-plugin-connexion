package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class PopulateRecursiveArgumentsPydanticTest : TestBase() {

    fun testPydanticAlias() {
        // We define a mock Pydantic structure. 
        // Note: effectively simulating what pydantic does for the type checker.
        myFixture.configureByText(
            "a.py",
            """
            # Mimic basic Pydantic parts
            from typing import dataclass_transform

            class Field:
                def __init__(self, alias=None, **kwargs): pass

            @dataclass_transform(field_specifiers=(Field,))
            class BaseModel:
                pass

            class User(BaseModel):
                name: str = Field(alias="userName")

            u = User(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments recursively")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            # Mimic basic Pydantic parts
            from typing import dataclass_transform

            class Field:
                def __init__(self, alias=None, **kwargs): pass

            @dataclass_transform(field_specifiers=(Field,))
            class BaseModel:
                pass

            class User(BaseModel):
                name: str = Field(alias="userName")

            u = User(userName=...)
            """.trimIndent()
        )
    }

    fun testPydanticAliasRecursive() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import dataclass_transform

            class Field:
                def __init__(self, alias=None, **kwargs): pass

            @dataclass_transform(field_specifiers=(Field,))
            class BaseModel:
                pass

            class Inner(BaseModel):
                val: int = Field(alias="value")

            class Outer(BaseModel):
                inner: Inner

            o = Outer(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments recursively")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import dataclass_transform

            class Field:
                def __init__(self, alias=None, **kwargs): pass

            @dataclass_transform(field_specifiers=(Field,))
            class BaseModel:
                pass

            class Inner(BaseModel):
                val: int = Field(alias="value")

            class Outer(BaseModel):
                inner: Inner

            o = Outer(inner=Inner(value=...))
            """.trimIndent()
        )
    }
}
