package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.doIntentionTest

class ReturnAnnotationTest : TestBase() {

    fun testReturnAnnotation_UnionType_UpdatesOnlyMatchingPart_WhenInvokedFromReturnExpression_Int() {
        myFixture.doIntentionTest(
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
            "BetterPy: Introduce custom type from int",
            renameTo = "Customint"
        )
    }

    fun testReturnAnnotation_UnionType_UpdatesOnlyMatchingPart_WhenInvokedFromReturnExpression_Str() {
        myFixture.doIntentionTest(
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
            "BetterPy: Introduce custom type from str",
            renameTo = "Customstr"
        )
    }
}
