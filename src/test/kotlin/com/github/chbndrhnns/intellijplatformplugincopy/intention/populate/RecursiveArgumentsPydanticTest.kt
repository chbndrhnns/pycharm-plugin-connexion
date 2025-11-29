package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.doIntentionTest

class RecursiveArgumentsPydanticTest : TestBase() {

    fun testPydanticAlias() {
        // We define a mock Pydantic structure. 
        // Note: effectively simulating what pydantic does for the type checker.
        myFixture.doIntentionTest(
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
            """,
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

            """,
            "Populate missing arguments recursively"
        )
    }

    fun testPydanticAliasRecursive() {
        myFixture.doIntentionTest(
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
            """,
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

            """,
            "Populate missing arguments recursively"
        )
    }
}
