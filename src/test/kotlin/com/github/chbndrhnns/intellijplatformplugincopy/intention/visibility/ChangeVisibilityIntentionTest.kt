package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

import fixtures.FakePopupHost
import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
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

    fun testChangeVisibility_NotAvailable_InConftest() {
        myFixture.assertIntentionNotAvailable(
            "conftest.py",
            """
            def so<caret>me_fixture():
                pass
            """,
            "Change visibility"
        )
    }

    fun testChangeVisibility_NotAvailable_InTestModule() {
        myFixture.assertIntentionNotAvailable(
            "test_foo.py",
            """
            def hel<caret>per():
                pass
            """,
            "Change visibility"
        )
    }

    fun testChangeVisibility_NotAvailable_ForTestFunction() {
        myFixture.assertIntentionNotAvailable(
            "utils.py",
            """
            def test_so<caret>mething():
                pass
            """,
            "Change visibility"
        )
    }

    fun testChangeVisibility_NotAvailable_ForTestClass() {
        myFixture.assertIntentionNotAvailable(
            "utils.py",
            """
            class Test<caret>_Class:
                pass
            """,
            "Change visibility"
        )
    }
}
