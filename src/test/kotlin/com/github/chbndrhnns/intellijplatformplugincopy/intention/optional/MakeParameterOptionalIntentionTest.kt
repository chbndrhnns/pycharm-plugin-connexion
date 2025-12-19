package com.github.chbndrhnns.intellijplatformplugincopy.intention.optional

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class MakeParameterOptionalIntentionTest : TestBase() {

    fun testMakeOptional_FunctionParameter_NoDefault() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def func(a<caret>rg: str):
                pass
            """,
            """
            def func(arg: str | None = None):
                pass
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_FunctionParameter_WithDefault() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def func(a<caret>rg: str = "default"):
                pass
            """,
            """
            def func(arg: str | None = "default"):
                pass
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_DataclassField_NoDefault() {
        myFixture.doIntentionTest(
            "a.py",
            """
            import dataclasses
            
            @dataclasses.dataclass
            class Data:
                fi<caret>eld: str
            """,
            """
            import dataclasses
            
            @dataclasses.dataclass
            class Data:
                field: str | None = None
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_DataclassField_WithDefault() {
        myFixture.doIntentionTest(
            "a.py",
            """
            import dataclasses
            
            @dataclasses.dataclass
            class Data:
                fi<caret>eld: str = "default"
            """,
            """
            import dataclasses
            
            @dataclasses.dataclass
            class Data:
                field: str | None = "default"
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_InstanceAttribute_NoDefault() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class Data:
                fi<caret>eld: str
            """,
            """
            class Data:
                field: str | None = None
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_NotAvailable_AlreadyOptional_Pipe() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def func(a<caret>rg: str | None = None):
                pass
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_NotAvailable_AlreadyOptional_Union() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import Union
            def func(a<caret>rg: Union[str, None] = None):
                pass
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_NotAvailable_AlreadyOptional_Optional() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import Optional
            def func(a<caret>rg: Optional[str] = None):
                pass
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_NotAvailable_NoAnnotation() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def func(a<caret>rg):
                pass
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_LocalVariable() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def func():
                local<caret>_var: str = "value"
            """,
            """
            def func():
                local_var: str | None = "value"
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_TopLevelVariable() {
        myFixture.doIntentionTest(
            "a.py",
            """
            glob<caret>al_var: int = 42
            """,
            """
            global_var: int | None = 42
            """,
            "BetterPy: Make optional"
        )
    }

    fun testMakeOptional_OnAnnotation_LocalVariable() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def func():
                local_var: st<caret>r = "value"
            """,
            """
            def func():
                local_var: str | None = "value"
            """,
            "BetterPy: Make optional"
        )
    }
}
