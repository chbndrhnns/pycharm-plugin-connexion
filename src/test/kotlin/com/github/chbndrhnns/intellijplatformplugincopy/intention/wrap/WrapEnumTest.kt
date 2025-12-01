package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class WrapEnumTest : TestBase() {

    fun testEnum_StringLiteral_WrapsWithVariant() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from enum import Enum

            class MyEnum(Enum):
                VARIANT = "variant"

            def f(e: MyEnum):
                pass

            f(<caret>"variant")
            """,
            """
            from enum import Enum

            class MyEnum(Enum):
                VARIANT = "variant"

            def f(e: MyEnum):
                pass

            f(MyEnum.VARIANT)
            """,
            "Wrap with MyEnum.VARIANT"
        )
    }

    fun testEnum_IntLiteral_WrapsWithVariant() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from enum import IntEnum

            class MyEnum(IntEnum):
                ONE = 1

            def f(e: MyEnum):
                pass

            f(<caret>1)
            """,
            """
            from enum import IntEnum

            class MyEnum(IntEnum):
                ONE = 1

            def f(e: MyEnum):
                pass

            f(MyEnum.ONE)
            """,
            "Wrap with MyEnum.ONE"
        )
    }

    fun testEnum_NoMatch_NoWrapSuggestion() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from enum import Enum

            class MyEnum(Enum):
                VARIANT = "variant"

            def f(e: MyEnum):
                pass

            f(<caret>"other")
            """,
            "Wrap with"
        )
    }
}
