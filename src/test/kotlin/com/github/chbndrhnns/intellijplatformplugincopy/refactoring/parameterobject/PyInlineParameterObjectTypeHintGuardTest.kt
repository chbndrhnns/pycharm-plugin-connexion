package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import fixtures.TestBase

class PyInlineParameterObjectTypeHintGuardTest : TestBase() {

    private val actionId = INLINE_PARAMETER_OBJECT_ACTION_ID

    fun testInlineBlockedForReturnTypeAnnotation() {
        assertInlineBlocked(
            """
            from dataclasses import dataclass


            @dataclass
            class CreateUserParams:
                first_name: str
                last_name: str


            def create_<caret>user(params: CreateUserParams) -> CreateUserParams:
                return params


            def main():
                create_user(CreateUserParams(first_name="Ada", last_name="Lovelace"))
            """
        )
    }

    fun testInlineBlockedForVariableAnnotation() {
        assertInlineBlocked(
            """
            from dataclasses import dataclass


            @dataclass
            class CreateUserParams:
                first_name: str
                last_name: str


            def create_<caret>user(params: CreateUserParams):
                print(params.first_name, params.last_name)


            params: CreateUserParams = CreateUserParams(first_name="Ada", last_name="Lovelace")
            create_user(params)
            """
        )
    }

    fun testInlineBlockedForContainerVariableAnnotation() {
        assertInlineBlocked(
            """
            from dataclasses import dataclass
            from typing import List


            @dataclass
            class CreateUserParams:
                first_name: str
                last_name: str


            def create_<caret>user(params: CreateUserParams):
                print(params.first_name, params.last_name)


            batch: List[CreateUserParams] = [
                CreateUserParams(first_name="Ada", last_name="Lovelace"),
            ]
            create_user(batch[0])
            """
        )
    }

    private fun assertInlineBlocked(before: String) {
        myFixture.configureByText("a.py", before.trimIndent())
        myFixture.doHighlighting()

        var dialogShown = false
        TestDialogManager.setTestDialog {
            dialogShown = true
            Messages.OK
        }

        try {
            invokeInlineAction()
            assertTrue("Expected a warning dialog to be shown", dialogShown)
            var expected = before.trimIndent()
            if (!expected.endsWith("\n") && myFixture.file.text.endsWith("\n")) {
                expected += "\n"
            }
            myFixture.checkResult(expected)
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
    }

    private fun invokeInlineAction() {
        val action = ActionManager.getInstance().getAction(actionId)
            ?: throw AssertionError("Action with ID '$actionId' not found")

        val dataContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor).add(CommonDataKeys.PSI_FILE, myFixture.file)
            .add(CommonDataKeys.VIRTUAL_FILE, myFixture.file.virtualFile).build()

        val event = AnActionEvent.createEvent(
            dataContext, action.templatePresentation.clone(), "unknown", ActionUiKind.NONE, null
        )

        action.actionPerformed(event)
    }
}
