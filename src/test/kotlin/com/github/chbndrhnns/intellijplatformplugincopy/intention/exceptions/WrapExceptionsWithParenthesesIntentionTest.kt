package com.github.chbndrhnns.intellijplatformplugincopy.intention.exceptions

import fixtures.TestBase
import fixtures.doIntentionTest

class WrapExceptionsWithParenthesesIntentionTest : TestBase() {

    fun testWrapExceptionsWithParentheses() {
        myFixture.doIntentionTest(
            "test_file.py",
            """
            def test_():
                try:
                    ...
                except Inde<caret>xError, KeyError:
                    pass
            """,
            """
            def test_():
                try:
                    ...
                except (IndexError, KeyError):
                    pass
            """,
            "Wrap exceptions with parentheses"
        )
    }

    fun testWrapExceptionsWithParentheses_SecondArgument() {
        myFixture.doIntentionTest(
            "test_file_2.py",
            """
            def test_():
                try:
                    ...
                except IndexError, Key<caret>Error:
                    pass
            """,
            """
            def test_():
                try:
                    ...
                except (IndexError, KeyError):
                    pass
            """,
            "Wrap exceptions with parentheses"
        )
    }
}
