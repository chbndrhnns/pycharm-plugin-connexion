package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.exceptions

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
            "BetterPy: Wrap exceptions with parentheses"
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
            "BetterPy: Wrap exceptions with parentheses"
        )
    }

    fun testPriorityAndIcon() {
        myFixture.configureByText(
            "test_file_priority.py",
            """
            def test_():
                try:
                    ...
                except Inde<caret>xError, KeyError:
                    pass
            """
        )

        var action = myFixture.availableIntentions.find { it.text == "BetterPy: Wrap exceptions with parentheses" }
        assertNotNull(action)

        while (action is com.intellij.codeInsight.intention.IntentionActionDelegate) {
            action = (action as com.intellij.codeInsight.intention.IntentionActionDelegate).delegate
        }

        assertTrue(
            "Action should implement PriorityAction",
            action is com.intellij.codeInsight.intention.PriorityAction
        )
        assertEquals(
            com.intellij.codeInsight.intention.PriorityAction.Priority.TOP,
            (action as com.intellij.codeInsight.intention.PriorityAction).priority
        )

        assertTrue("Action should implement Iconable", action is com.intellij.openapi.util.Iconable)
        assertEquals(
            com.intellij.icons.AllIcons.Actions.QuickfixBulb,
            (action as com.intellij.openapi.util.Iconable).getIcon(0)
        )
    }
}
