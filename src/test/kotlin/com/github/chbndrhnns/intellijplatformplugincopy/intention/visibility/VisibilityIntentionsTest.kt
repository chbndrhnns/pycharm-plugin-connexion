package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

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
            "Make public"
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
            "Make public"
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
            "Make public"
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
            "Make public"
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
            "Make private"
        )
    }

    fun testMakePrivate_OnAlreadyPrivate_NotAvailable() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def _in<caret>ternal():
                pass
            """,
            "Make private"
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
            "Make private"
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
            "Make private"
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
            "Make private"
        )
    }
}
