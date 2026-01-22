package com.github.chbndrhnns.intellijplatformplugincopy.features.connexion

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.TestActionEvent
import fixtures.TestBase

class GoToOpenApiOperationActionTest : TestBase() {

    fun testActionAvailability() {
        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "api",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        val psiFile = myFixture.configureByText(
            "api.py", """
            def list_pets():
                pass

            def <caret>other_function():
                pass
        """.trimIndent()
        )

        val action = ActionManager.getInstance().getAction("Connexion.GoToOpenApiOperation")

        val dataContext = com.intellij.ide.DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)

        // Case 1: On unlinked function (other_function) - current caret position
        var event = TestActionEvent.createTestEvent(action, dataContext)
        action.update(event)
        assertFalse("Action should not be available for unlinked function", event.presentation.isEnabledAndVisible)

        // Case 2: On linked function
        val offset = psiFile.text.indexOf("def list_pets")
        myFixture.editor.caretModel.moveToOffset(offset)

        event = TestActionEvent.createTestEvent(action, dataContext)
        action.update(event)
        assertTrue("Action should be available for linked function", event.presentation.isEnabledAndVisible)
    }
}
