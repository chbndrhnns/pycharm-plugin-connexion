package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.doIntentionTest

class WrapCustomEnumTest : TestBase() {

    fun testCustomEnumMixin_IntLiteral_WrapsWithCustomType() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from enum import Enum
            
            class MyType:
                def __init__(self, x): pass

            class MyEnum(MyType, Enum):
                ONE = <caret>1
            """,
            """
            from enum import Enum
            
            class MyType:
                def __init__(self, x): pass

            class MyEnum(MyType, Enum):
                ONE = MyType(1)
            """,
            "BetterPy: Wrap with MyType"
        )
    }

    fun testFloatEnumMixin_IntLiteral_WrapsWithFloat() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from enum import Enum
            
            class MyEnum(float, Enum):
                ONE = <caret>1
            """,
            """
            from enum import Enum
            
            class MyEnum(float, Enum):
                ONE = float(1)
            """,
            "BetterPy: Wrap with float"
        )
    }

    // Regression check for StrEnum to ensure it still works with generic logic
    fun testStrEnum_IntLiteral_WrapsWithStr() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from enum import StrEnum

            class Variant(StrEnum):
                ONE = <caret>1
            """,
            """
            from enum import StrEnum

            class Variant(StrEnum):
                ONE = str(1)
            """,
            "BetterPy: Wrap with str"
        )
    }
}
