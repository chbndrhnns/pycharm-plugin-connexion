package com.github.chbndrhnns.betterpy.features.intentions.customtype

import fixtures.TestBase
import fixtures.doRefactoringActionTest

class ReturnAnnotationTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testReturnAnnotation_UnionType_UpdatesOnlyMatchingPart_WhenInvokedFromReturnExpression_Int() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def get() -> int | str | None:
                return 1<caret>23
            """,
            """
            class Customint(int):
                pass


            def get() -> Customint | str | None:
                return Customint(123)
            """,
            actionId,
            renameTo = "Customint"
        )
    }

    fun testReturnAnnotation_UnionType_UpdatesOnlyMatchingPart_WhenInvokedFromReturnExpression_Str() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def get() -> int | str | None:
                return "he<caret>llo"
            """,
            """
            class Customstr(str):
                __slots__ = ()


            def get() -> int | Customstr | None:
                return Customstr("hello")
            """,
            actionId,
            renameTo = "Customstr"
        )
    }
}
