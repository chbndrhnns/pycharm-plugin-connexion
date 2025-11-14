package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

/**
 * Wrap intention behavior when the expected type is a union (without forward refs).
 */
class WrapUnionChoiceTest : TestBase() {

    fun testUnionAutoSelect_PathOverStr() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            from typing import Union

            a: Union[Path, str] = <caret>"val"
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with Path()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from pathlib import Path
            from typing import Union

            a: Union[Path, str] = Path("val")
            """.trimIndent()
        )
    }

    fun testUnionChooser_WithTypingUnionContainingOr() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
            from typing import NewType, Union

            One = NewType("One", str)
            Two = NewType("Two", str)

            a: Union[One | Two] = <caret>"val"
            """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            myFixture.checkResult(
                """
            from typing import NewType, Union

            One = NewType("One", str)
            Two = NewType("Two", str)

            a: Union[One | Two] = One("val")
            """.trimIndent()
            )
        } finally {
            com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapWithExpectedTypeIntentionHooks.popupHost =
                null
        }
    }
}
