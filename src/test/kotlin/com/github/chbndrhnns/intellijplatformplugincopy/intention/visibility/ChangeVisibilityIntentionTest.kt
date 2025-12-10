package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

import fixtures.FakePopupHost
import fixtures.TestBase
import fixtures.doIntentionTest

class ChangeVisibilityIntentionTest : TestBase() {

    private val fakePopupHost = FakePopupHost()

    override fun setUp() {
        super.setUp()
        ChangeVisibilityIntentionHooks.popupHost = fakePopupHost
    }

    override fun tearDown() {
        ChangeVisibilityIntentionHooks.popupHost = null
        super.tearDown()
    }

    fun testChangeVisibility_PrivateToPublic() {
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

    fun testChangeVisibility_PublicToPrivate() {
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
}
