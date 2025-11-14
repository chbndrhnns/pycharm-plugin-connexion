package com.github.chbndrhnns.intellijplatformplugincopy.intention.unwrap

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Basic unwrap intention behavior: assignments, arguments, returns, and
 * simple container cases for [UnwrapToExpectedTypeIntention].
 */
class UnwrapBasicTest : BasePlatformTestCase() {

    fun testAnnotatedAssignment_NewType_UnwrapsToUnderlyingType() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            x: int = <caret>UserId(42)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            x: int = 42
            """.trimIndent()
        )
    }

    fun testArgument_NewType_UnwrapsToParameterType() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(<caret>UserId(10))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(10)
            """.trimIndent()
        )
    }

    fun testArgument_NewType_CaretOnInnerLiteral_UnwrapsToParameterType() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(UserId(<caret>10))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(10)
            """.trimIndent()
        )
    }

    fun testArgument_NewType_CaretOnInnerParenthesis_UnwrapsToParameterType() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(UserId<caret>(10))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def f(user_id: int) -> None:
                ...

            f(10)
            """.trimIndent()
        )
    }

    fun testReturn_NewType_UnwrapsToReturnType() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def get_user_id() -> int:
                return <caret>UserId(1)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap UserId()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)

            def get_user_id() -> int:
                return 1
            """.trimIndent()
        )
    }

    fun testContainerConstructor_List_UnwrapNotOffered() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import List

            xs: List[int] = <caret>list([1, 2, 3])
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.availableIntentions.find { it.text == "Unwrap list()" }
        assertNull("Unwrap intention should not be offered for container constructors", intention)
    }
}
