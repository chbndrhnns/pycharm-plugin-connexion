package com.github.chbndrhnns.betterpy.features.intentions.customtype

import fixtures.TestBase
import fixtures.doRefactoringActionTest

class AnnotationTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testAnnotatedAssignment_UnionType_UpdatesOnlyMatchingPart() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            val: int | str | None = <caret>2
            """,
            """
            class Customint(int):
                pass
            
            
            val: Customint | str | None = Customint(2)
            """,
            actionId,
            renameTo = "Customint"
        )
    }

    fun testAnnotatedParameter_UnionType_UpdatesOnlyMatchingPart() {
        myFixture.doRefactoringActionTest(
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
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testAnnotatedAssignment_UnionType_String_UpdatesOnlyMatchingPart() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            val: int | str | None = <caret>"hello"
            """,
            """
            class Customstr(str):
                __slots__ = ()
            
            
            val: int | Customstr | None = Customstr("hello")
            """,
            actionId,
            renameTo = "Customstr"
        )
    }
}
