package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.doIntentionTest

class ParameterTest : TestBase() {

    fun testParameterDefaultValue_UnionType_UpdatesAnnotationAndWrapsValue() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(val: int | str | None = <caret>"2"): 
                pass
            """,
            """
            class Customstr(str):
                pass
            
            
            def do(val: int | Customstr | None = Customstr("2")): 
                pass
            """,
            "Introduce custom type from str"
        )
    }

    fun testParameterDefaultValue_SimpleType_UpdatesAnnotationAndWrapsValue() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(val: int = <caret>1): 
                pass
            """,
            """
            class Customint(int):
                pass
            
            
            def do(val: Customint = Customint(1)): 
                pass
            """,
            "Introduce custom type from int"
        )
    }
}
