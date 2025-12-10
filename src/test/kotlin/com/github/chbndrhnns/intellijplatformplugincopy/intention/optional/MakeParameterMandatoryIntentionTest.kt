package com.github.chbndrhnns.intellijplatformplugincopy.intention.optional

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class MakeParameterMandatoryIntentionTest : TestBase() {

    fun testMakeMandatory_FunctionParameter_WithDefaultNone() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def func(a<caret>rg: str | None = None):
                pass
            """,
            """
            def func(arg: str):
                pass
            """,
            "Make mandatory"
        )
    }

    fun testMakeMandatory_FunctionParameter_WithDefaultNone_Optional() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Optional
            def func(a<caret>rg: Optional[str] = None):
                pass
            """,
            """
            from typing import Optional
            def func(arg: str):
                pass
            """,
            "Make mandatory"
        )
    }

    fun testMakeMandatory_FunctionParameter_WithDefaultNone_Union() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Union
            def func(a<caret>rg: Union[str, None] = None):
                pass
            """,
            """
            from typing import Union
            def func(arg: str):
                pass
            """,
            "Make mandatory"
        )
    }

    fun testMakeMandatory_FunctionParameter_WithDefaultNone_Union_Reverse() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Union
            def func(a<caret>rg: Union[None, str] = None):
                pass
            """,
            """
            from typing import Union
            def func(arg: str):
                pass
            """,
            "Make mandatory"
        )
    }

    fun testMakeMandatory_DataclassField_WithDefaultNone() {
        myFixture.doIntentionTest(
            "a.py",
            """
            import dataclasses
            
            @dataclasses.dataclass
            class Data:
                fi<caret>eld: str | None = None
            """,
            """
            import dataclasses
            
            @dataclasses.dataclass
            class Data:
                field: str
            """,
            "Make mandatory"
        )
    }

    fun testMakeMandatory_NotAvailable_AlreadyMandatory() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def func(a<caret>rg: str):
                pass
            """,
            "Make mandatory"
        )
    }

    fun testMakeMandatory_UpdatesCallSites() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def func(a<caret>rg: str | None = None):
                pass
            
            func()
            """,
            """
            def func(arg: str):
                pass
            
            
            func(arg=...)
            """,
            "Make mandatory"
        )
    }
}
