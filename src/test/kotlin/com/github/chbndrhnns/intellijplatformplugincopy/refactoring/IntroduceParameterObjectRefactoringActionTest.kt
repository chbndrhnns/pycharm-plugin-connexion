package com.github.chbndrhnns.intellijplatformplugincopy.refactoring

import com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.withMockIntroduceParameterObjectDialog
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.refactoring.actions.BaseRefactoringAction
import fixtures.TestBase

class IntroduceParameterObjectRefactoringActionTest : TestBase() {

    fun testActionExtendsBaseRefactoringAction() {
        val action = IntroduceParameterObjectRefactoringAction()
        assertTrue(
            "Action must extend BaseRefactoringAction to appear in 'Refactor This' popup",
            action is BaseRefactoringAction
        )
    }

    fun testActionIsRegistered() {
        val actionManager = ActionManager.getInstance()
        val action =
            actionManager.getAction("com.github.chbndrhnns.intellijplatformplugincopy.refactoring.IntroduceParameterObjectRefactoringAction")
        assertNotNull("Action should be registered in plugin.xml", action)
        assertTrue(
            "Registered action should be IntroduceParameterObjectRefactoringAction",
            action is IntroduceParameterObjectRefactoringAction
        )
    }

    fun testActionIsAvailableForFunctionWithMultipleParameters() {
        myFixture.configureByText(
            "test.py", """
            def foo(a: int, b<caret>: str, c: float):
                pass
        """.trimIndent()
        )

        val action = IntroduceParameterObjectRefactoringAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = createTestActionEvent(action, dataContext)
        action.update(event)

        assertTrue(
            "Action should be enabled for function with multiple parameters",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testActionIsAvailableOnFunctionNameAtDeclarationSite() {
        myFixture.configureByText(
            "test.py", """
            def fo<caret>o(a: int, b: str, c: float):
                pass
        """.trimIndent()
        )

        val action = IntroduceParameterObjectRefactoringAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = createTestActionEvent(action, dataContext)
        action.update(event)

        assertTrue(
            "Action should be enabled when caret is on function name at declaration site",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testActionIsAvailableOnFunctionNameAtCallSite() {
        myFixture.configureByText(
            "test.py", """
            def foo(a: int, b: str, c: float):
                pass

            fo<caret>o(1, "hello", 3.14)
        """.trimIndent()
        )

        val action = IntroduceParameterObjectRefactoringAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = createTestActionEvent(action, dataContext)
        action.update(event)

        assertTrue(
            "Action should be enabled when caret is on function name at call site",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testActionIsNotAvailableForFunctionWithOneParameter() {
        myFixture.configureByText(
            "test.py", """
            def foo(a<caret>: int):
                pass
        """.trimIndent()
        )

        val action = IntroduceParameterObjectRefactoringAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = createTestActionEvent(action, dataContext)
        action.update(event)

        assertFalse(
            "Action should not be enabled for function with only one parameter",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testActionIsNotAvailableOutsideFunction() {
        myFixture.configureByText(
            "test.py", """
            x = 1<caret>
        """.trimIndent()
        )

        val action = IntroduceParameterObjectRefactoringAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = createTestActionEvent(action, dataContext)
        action.update(event)

        assertFalse(
            "Action should not be enabled outside a function",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testActionPerformsRefactoring() {
        myFixture.configureByText(
            "test.py", """
            def foo(a: int, b<caret>: str, c: float):
                return a + len(b) + c
            
            foo(1, "hello", 3.14)
        """.trimIndent()
        )

        val action = IntroduceParameterObjectRefactoringAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        withMockIntroduceParameterObjectDialog {
            val event = createTestActionEvent(action, dataContext)
            action.actionPerformed(event)
        }

        val result = myFixture.file.text
        assertTrue("Should create a dataclass", result.contains("@dataclass"))
        assertTrue("Should create FooParams class", result.contains("class FooParams"))
    }

    fun testActionIgnoresSelfParameter() {
        myFixture.configureByText(
            "test.py", """
            class MyClass:
                def method(self, a: int, b<caret>: str):
                    pass
        """.trimIndent()
        )

        val action = IntroduceParameterObjectRefactoringAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = createTestActionEvent(action, dataContext)
        action.update(event)

        assertTrue(
            "Action should be enabled for method with self and two other parameters",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testActionIgnoresVariadicParameters() {
        myFixture.configureByText(
            "test.py", """
            def foo(a: int, b<caret>: str, *args, **kwargs):
                pass
        """.trimIndent()
        )

        val action = IntroduceParameterObjectRefactoringAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = createTestActionEvent(action, dataContext)
        action.update(event)

        assertTrue(
            "Action should be enabled and ignore *args/**kwargs",
            event.presentation.isEnabledAndVisible
        )
    }

    private fun createTestActionEvent(
        action: AnAction,
        dataContext: com.intellij.openapi.actionSystem.DataContext
    ): com.intellij.openapi.actionSystem.AnActionEvent {
        return com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext(
            "",
            null,
            dataContext
        )
    }
}
