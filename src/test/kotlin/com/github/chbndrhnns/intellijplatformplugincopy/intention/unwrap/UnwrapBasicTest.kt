package com.github.chbndrhnns.intellijplatformplugincopy.intention.unwrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

/**
 * Basic unwrap intention behavior: assignments, arguments, returns, and
 * simple container cases for [UnwrapToExpectedTypeIntention].
 */
class UnwrapBasicTest : TestBase() {

    fun testAnnotatedAssignment_NewType_UnwrapsToUnderlyingType() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            x: int = <caret>UserId(42)
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            x: int = 42
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testArgument_NewType_UnwrapsToParameterType() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(<caret>UserId(10))
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(10)
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testArgument_NewType_CaretOnInnerLiteral_UnwrapsToParameterType() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(UserId(<caret>10))
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(10)
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testArgument_NewType_CaretOnInnerParenthesis_UnwrapsToParameterType() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(UserId<caret>(10))
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(10)
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testReturn_NewType_UnwrapsToReturnType() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def get_user_id() -> int:
                return <caret>UserId(1)
            """,
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def get_user_id() -> int:
                return 1
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testContainerConstructor_List_UnwrapNotOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import List

            xs: List[int] = <caret>list([1, 2, 3])
            """,
            "BetterPy: Unwrap list()"
        )
    }

    fun testAnnotatedAssignment_Int_RespectsAnnotatedExpectedType() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Annotated, NewType

            UserId = NewType("UserId", int)

            x: Annotated[int, "meta"] = <caret>UserId(42)
            """,
            """
            from typing import Annotated, NewType

            UserId = NewType("UserId", int)

            x: Annotated[int, "meta"] = 42
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testAnnotatedAssignment_DictGet_NotOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def do():
                group_access = {}
                access_level: str = group_access.<caret>get("access_level")
            """,
            "BetterPy: Unwrap get()"
        )
    }
}
