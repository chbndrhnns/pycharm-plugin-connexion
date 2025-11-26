package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapWithExpectedTypeIntentionHooks
import fixtures.FakePopupHost
import fixtures.TestBase

/**
 * UI / presentation tests for wrap intention with forward-referenced types.
 */
class WrapForwardRefsPresentationTest : TestBase() {

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

    fun testUnionChooserShowsFqnForForwardRef_newTypesQuotedUnion() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from typing import NewType

                CloudId = NewType("CloudId", str)
                Namespace = NewType("Namespace", str)

                def f(x: "CloudId | Namespace") -> None:
                    ...

                f(<caret>"abc")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            // Ensure chooser labels use fully qualified names for NewType-based forward refs
            assertEquals(listOf("CloudId (a.CloudId)", "Namespace (a.Namespace)"), fake.lastLabels)
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }
}
