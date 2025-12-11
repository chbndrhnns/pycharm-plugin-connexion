package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.doIntentionTest

class AnnotationTest : TestBase() {

    fun testAnnotatedAssignment_UnionType_UpdatesOnlyMatchingPart() {
        myFixture.doIntentionTest(
            "a.py",
            """
            val: int | str | None = <caret>2
            """,
            """
            class Customint(int):
                pass
            
            
            val: Customint | str | None = Customint(2)
            """,
            "Introduce custom type from int",
            renameTo = "Customint"
        )
    }

    fun testAnnotatedParameter_UnionType_UpdatesOnlyMatchingPart() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class CustomWrapper: ...


            def f(x: CustomWrapper | str) -> None:
                pass
            
            
            f("a<caret>bc")
            """,
            """
            class Customstr(str):
                __slots__ = ()


            class CustomWrapper: ...


            def f(x: CustomWrapper | Customstr) -> None:
                pass
            
            
            f(Customstr("abc"))
            """,
            "Introduce custom type from str",
            renameTo = "Customstr"
        )
    }

    fun testAnnotatedAssignment_UnionType_String_UpdatesOnlyMatchingPart() {
        myFixture.doIntentionTest(
            "a.py",
            """
            val: int | str | None = <caret>"hello"
            """,
            """
            class Customstr(str):
                __slots__ = ()
            
            
            val: int | Customstr | None = Customstr("hello")
            """,
            "Introduce custom type from str",
            renameTo = "Customstr"
        )
    }
}
