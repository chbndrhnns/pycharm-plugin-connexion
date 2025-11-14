package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapWithExpectedTypeIntentionHooks

class ForwardRefsTest : TestBase() {

    fun testNonContainerForwardRef_param() {
        myFixture.configureByText(
            "a.py",
            """
            from __future__ import annotations

            def f(x: 'A') -> None:
                ...

            class A:
                def __init__(self, value): ...

            f(<caret>value)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with A()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from __future__ import annotations

            def f(x: 'A') -> None:
                ...

            class A:
                def __init__(self, value): ...

            f(A(value))
            """.trimIndent()
        )
    }

    fun testUnionChooserShowsFqnForForwardRef_acrossModules_qualifiedStrings() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.addFileToProject(
                "pkg/__init__.py",
                """"""
            )
            myFixture.addFileToProject(
                "pkg/b.py",
                """
                class User:
                    def __init__(self, value: str):
                        self.value = value

                class Token:
                    def __init__(self, value: str):
                        self.value = value
                """.trimIndent()
            )

            myFixture.configureByText(
                "a.py",
                """
                from .pkg.b import User, Token

                def f(x: "User | Token") -> None:
                    ...

                f(<caret>"abc")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            assertEquals(listOf("User (pkg.b.User)", "Token (pkg.b.Token)"), fake.lastLabels)
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }

    fun testUnionChooserShowsFqnForForwardRef_mixedQuotedAndRef() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from __future__ import annotations

                class User:
                    def __init__(self, value: str):
                        self.value = value

                class Token:
                    def __init__(self, value: str):
                        self.value = value

                def f(x: 'User' | Token) -> None:
                    ...

                f(<caret>"abc")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            // Expect FQNs for both candidates even when one comes from a quoted forward ref
            assertEquals(listOf("User (a.User)", "Token (a.Token)"), fake.lastLabels)
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }

    fun testUnionChooserShowsFqnForForwardRef_quotedUnion() {
        val fake = FakePopupHost().apply { selectedIndex = 1 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from __future__ import annotations

                class User:
                    def __init__(self, value: str):
                        self.value = value

                class Token:
                    def __init__(self, value: str):
                        self.value = value

                def f(x: "User|Token") -> None:
                    ...

                f(<caret>"abc")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            // Ensure chooser labels include fully qualified names for both quoted members
            assertEquals(listOf("User (a.User)", "Token (a.Token)"), fake.lastLabels)
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }

    fun testUnionWithForwardRef_mixed() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from __future__ import annotations

                class B:
                    def __init__(self, v): ...

                def f(x: 'A' | B): ...

                class A:
                    def __init__(self, v): ...

                f(<caret>v)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            // We should offer both A and B; order is not strictly important, but A first is fine
            assertTrue(fake.lastLabels.any { it.startsWith("A") })
            assertTrue(fake.lastLabels.any { it.startsWith("B") })

            // First gets applied (A)
            myFixture.checkResult(
                """
                from __future__ import annotations

                class B:
                    def __init__(self, v): ...

                def f(x: 'A' | B): ...

                class A:
                    def __init__(self, v): ...

                f(A(v))
                """.trimIndent()
            )
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }

    fun testContainerWithForwardRef_itemWrap() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from __future__ import annotations
                from typing import List

                def f(xs: List['A']): ...

                class A:
                    def __init__(self, v): ...

                f([<caret>v])
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with A()")
            myFixture.launchAction(intention)
            myFixture.checkResult(
                """
                from __future__ import annotations
                from typing import List

                def f(xs: List['A']): ...

                class A:
                    def __init__(self, v): ...

                f([A(v)])
                """.trimIndent()
            )
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }

    fun testUnresolvedForwardRef_offersTextualWrap() {
        myFixture.configureByText(
            "a.py",
            """
            from __future__ import annotations

            def f(x: 'Ghost'): ...

            f(<caret>v)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with Ghost()")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from __future__ import annotations

            def f(x: 'Ghost'): ...

            f(Ghost(v))
            """.trimIndent()
        )
    }

    fun testQuotedUnionNewTypes_areSplitAndChosen() {
        val fake = FakePopupHost().apply { selectedIndex = 1 } // Expect order Two, One → choose One
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from typing import NewType

                One = NewType("One", str)
                Two = NewType("Two", int)

                def do1(arg: "Two|One") -> None:
                    return arg

                do1(<caret>"abc")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            // Ensure chooser contained both options (allow labels to include FQNs)
            assertTrue(fake.lastLabels.any { it.startsWith("Two") })
            assertTrue(fake.lastLabels.any { it.startsWith("One") })

            myFixture.checkResult(
                """
                from typing import NewType

                One = NewType("One", str)
                Two = NewType("Two", int)

                def do1(arg: "Two|One") -> None:
                    return arg

                do1(One("abc"))
                """.trimIndent()
            )
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }

    fun testUnionChooserShowsFqnForForwardRef_newTypesQuotedUnion() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",

                """
                from typing import NewType

                One = NewType("One", str)
                Two = NewType("Two", int)

                def do1(arg: "Two|One") -> None:
                    return arg

                do1(<caret>"abc")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            // FQNs should be shown for NewType aliases resolved as targets in the same file
            assertEquals(listOf("Two (a.Two)", "One (a.One)"), fake.lastLabels)
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }
}
