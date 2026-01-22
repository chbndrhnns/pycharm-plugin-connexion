package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.visibility

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class VisibilityIntentionsTest : TestBase() {

    fun testMakePublic_OnClass_RemovesLeadingUnderscore() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class _In<caret>ternal:
                pass
            """,
            """
            class Internal:
                pass
            """,
            "BetterPy: Change visibility: make public"
        )
    }

    fun testMakePublic_OnDunderMethod_NotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class C:
                def __in<caret>it__(self):
                    pass
            """,
            "BetterPy: Change visibility: make public"
        )
    }

    fun testMakePublic_OnMangledMethod_Demangles() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class C:
                def __man<caret>gled(self):
                    pass
            """,
            """
            class C:
                def mangled(self):
                    pass
            """,
            "BetterPy: Change visibility: make public"
        )
    }

    fun testMakePublic_OnTopLevelFunction_RemovesLeadingUnderscore() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def _in<caret>ternal():
                pass
            """,
            """
            def internal():
                pass
            """,
            "BetterPy: Change visibility: make public"
        )
    }

    fun testMakePrivate_OnClass_AddsSingleUnderscore() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class Pu<caret>blic:
                pass
            """,
            """
            class _Public:
                pass
            """,
            "BetterPy: Change visibility: make private"
        )
    }

    fun testMakePrivate_OnAlreadyPrivate_NotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def _in<caret>ternal():
                pass
            """,
            "BetterPy: Change visibility: make private"
        )
    }

    fun testMakePrivate_OnTopLevelFunction_AddsSingleUnderscore() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def pu<caret>blic():
                pass
            """,
            """
            def _public():
                pass
            """,
            "BetterPy: Change visibility: make private"
        )
    }

    fun testMakePrivate_OnMethod_AddsSingleUnderscore() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class C:
                def pu<caret>blic(self):
                    pass
            """,
            """
            class C:
                def _public(self):
                    pass
            """,
            "BetterPy: Change visibility: make private"
        )
    }

    fun testMakePrivate_OnDunderMethod_NotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class C:
                def __ca<caret>ll__(self):
                    pass
            """,
            "BetterPy: Change visibility: make private"
        )
    }

    fun testMakePrivate_InMethodBody_NotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class C:
                def public(self):
                    p<caret>ass
            """,
            "BetterPy: Change visibility: make private"
        )
    }

    fun testMakePublic_InMethodBody_NotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class C:
                def _private(self):
                    p<caret>ass
            """,
            "BetterPy: Change visibility: make public"
        )
    }
}
