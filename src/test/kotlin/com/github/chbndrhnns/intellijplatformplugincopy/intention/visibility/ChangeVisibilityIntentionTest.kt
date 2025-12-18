package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class ChangeVisibilityIntentionTest : TestBase() {

    fun testChangeVisibility_PrivateToPublic() {
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
            "Change visibility: make public"
        )
    }

    fun testChangeVisibility_PublicToPrivate() {
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
            "Change visibility: make private"
        )
    }

    fun testChangeVisibility_NotAvailable_InConftest() {
        myFixture.assertIntentionNotAvailable(
            "conftest.py",
            """
            def so<caret>me_fixture():
                pass
            """,
            "Change visibility: make private"
        )
    }

    fun testChangeVisibility_NotAvailable_InTestModule() {
        myFixture.assertIntentionNotAvailable(
            "test_foo.py",
            """
            def hel<caret>per():
                pass
            """,
            "Change visibility: make private"
        )
    }

    fun testChangeVisibility_NotAvailable_ForTestFunction() {
        myFixture.assertIntentionNotAvailable(
            "utils.py",
            """
            def test_so<caret>mething():
                pass
            """,
            "Change visibility: make private"
        )
    }

    fun testChangeVisibility_NotAvailable_ForTestClass() {
        myFixture.assertIntentionNotAvailable(
            "utils.py",
            """
            class Test<caret>_Class:
                pass
            """,
            "Change visibility: make private"
        )
    }
}
