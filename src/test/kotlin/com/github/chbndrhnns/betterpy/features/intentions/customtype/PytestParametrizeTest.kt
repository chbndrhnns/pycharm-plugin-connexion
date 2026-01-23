package com.github.chbndrhnns.betterpy.features.intentions.customtype

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.RenameDialogInterceptor
import com.intellij.ui.UiInterceptors
import fixtures.TestBase

class PytestParametrizeTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    private fun performRefactoringAction() {
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(actionId)
            ?: throw AssertionError("Action $actionId not found")

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
        action.actionPerformed(event)
    }

    fun testParametrizeDecorator_WrapsListItems() {
        UiInterceptors.register(RenameDialogInterceptor("Arg"))

        myFixture.configureByText(
            "test_a.py",
            """
            import pytest
            
            
            @pytest.mark.parametrize("arg", [1, 2, 3])
            def test_(arg):
                assert ar<caret>g
            """.trimIndent()
        )
        myFixture.doHighlighting()
        performRefactoringAction()

        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val actual = myFixture.file.text

        // Verify the key parts are present
        assertTrue("Should contain Arg class", actual.contains("class Arg(int):"))
        assertTrue("List items should be wrapped", actual.contains("Arg(1)"))
        assertTrue("List items should be wrapped", actual.contains("Arg(2)"))
        assertTrue("List items should be wrapped", actual.contains("Arg(3)"))
        assertTrue("Should contain parametrize decorator", actual.contains("@pytest.mark.parametrize"))

        // Verify the expression in assert is NOT wrapped (should be just "arg", not "Arg(arg)")
        assertTrue("Assert should reference arg directly", actual.contains("assert arg"))
        assertFalse("Assert should NOT wrap arg", actual.contains("assert Arg(arg)"))
    }

    fun testParametrizeDecorator_CaretOnParameterName_AddsAnnotation() {
        UiInterceptors.register(RenameDialogInterceptor("Arg"))

        myFixture.configureByText(
            "test_a.py",
            """
            import pytest
            
            
            @pytest.mark.parametrize("arg", [1, 2, 3])
            def test_(a<caret>rg):
                assert arg
            """.trimIndent()
        )
        myFixture.doHighlighting()
        performRefactoringAction()

        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val actual = myFixture.file.text

        // Verify the key parts are present
        assertTrue("Should contain Arg class", actual.contains("class Arg(int):"))
        assertTrue("List items should be wrapped", actual.contains("Arg(1)"))
        assertTrue("List items should be wrapped", actual.contains("Arg(2)"))
        assertTrue("List items should be wrapped", actual.contains("Arg(3)"))

        // Verify parameter has annotation, not wrapped
        assertTrue("Parameter should have annotation", actual.contains("def test_(arg: Arg):"))
        assertFalse("Parameter should NOT be wrapped", actual.contains("def test_(Arg(arg))"))
        assertFalse("Parameter should NOT be wrapped", actual.contains("def test_(CustomInt(arg))"))
    }

}
