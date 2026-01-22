package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.wrap

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class StarredExpressionsTest : TestBase() {

    fun testWrapWithExpectedType_UnavailableOnStarredExpression() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(x: int): pass
            l = ["s"]
            f(*<caret>l)
            """,
            "BetterPy: Wrap with expected type"
        )
    }

    fun testWrapWithExpectedType_UnavailableOnDoubleStarredExpression() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(x: int): pass
            d = {"x": "s"}
            f(**<caret>d)
            """,
            "BetterPy: Wrap with expected type"
        )
    }

    fun testUnwrapToExpectedType_UnavailableOnStarredExpression() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            def f(x: int): pass
            val = UserId(1)
            f(*<caret>val) 
            """,
            "BetterPy: Unwrap UserId()"
        )
    }

    fun testWrapWithExpectedType_UnavailableOnStarredNonIterable() {
        // Expected: Iterable (mapped to list). Actual: int.
        // Intention should NOT be available according to issue.
        // If it IS available, this test will fail, confirming reproduction.
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            l = [*<caret>1]
            """,
            "BetterPy: Wrap with expected type"
        )
    }

    fun testIntroduceCustomType_UnavailableOnStarredExpression() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x): pass
            val = [1]
            f(*<caret>list(val))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val action = ActionManager.getInstance().getAction(
            "com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"
        )
        assertNotNull("Action should be registered", action)

        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()
        val event = TestActionEvent.createEvent(
            action,
            dataContext,
            action.templatePresentation.clone(),
            "",
            ActionUiKind.NONE,
            null
        )
        action.update(event)

        assertFalse("Action should be disabled on starred expression", event.presentation.isEnabled)
    }

    fun testWrapItems_UnavailableOnStarredExpression() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(x: int): pass
            f(*[<caret>"s"])
            """,
            "Wrap items"
        )
    }
}