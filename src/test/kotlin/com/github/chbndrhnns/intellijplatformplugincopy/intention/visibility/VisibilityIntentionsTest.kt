package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

import fixtures.FakePopupHost
import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class VisibilityIntentionsTest : TestBase() {

    private val fakePopupHost = FakePopupHost()

    override fun setUp() {
        super.setUp()
        ChangeVisibilityIntentionHooks.popupHost = fakePopupHost
    }

    override fun tearDown() {
        ChangeVisibilityIntentionHooks.popupHost = null
        super.tearDown()
    }

    fun testMakePublic_OnClass_RemovesLeadingUnderscore() {
        fakePopupHost.selectedIndex = 0 // Public
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
            "Change visibility"
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
            "Change visibility"
        )
    }

    fun testMakePublic_OnMangledMethod_Demangles() {
        fakePopupHost.selectedIndex = 0 // Public
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
            "Change visibility"
        )
    }

    fun testMakePublic_OnTopLevelFunction_RemovesLeadingUnderscore() {
        fakePopupHost.selectedIndex = 0 // Public
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
            "Change visibility"
        )
    }

    fun testMakePrivate_OnClass_AddsSingleUnderscore() {
        fakePopupHost.selectedIndex = 1 // Private
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
            "Change visibility"
        )
    }

    fun testMakePrivate_OnAlreadyPrivate_NoChange() {
        fakePopupHost.selectedIndex = 1 // Private
        // Intention is available, but selecting Private should result in no change
        myFixture.doIntentionTest(
            "a.py",
            """
            def _in<caret>ternal():
                pass
            """,
            """
            def _internal():
                pass
            """,
            "Change visibility"
        )
    }

    fun testMakePrivate_OnTopLevelFunction_AddsSingleUnderscore() {
        fakePopupHost.selectedIndex = 1 // Private
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
            "Change visibility"
        )
    }

    fun testMakePrivate_OnMethod_AddsSingleUnderscore() {
        fakePopupHost.selectedIndex = 1 // Private
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
            "Change visibility"
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
            "Change visibility"
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
            "Change visibility"
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
            "Change visibility"
        )
    }
}
