package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import fixtures.TestBase

class PyIntroduceParameterObjectActionTest : TestBase() {

    fun testActionUpdateThread() {
        val action = PyIntroduceParameterObjectAction()
        assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
    }

    fun testActionAvailability() {
        myFixture.configureByText("a.py", """
            def fo<caret>o(a, b):
                pass
        """.trimIndent())
        
        val action = PyIntroduceParameterObjectAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()
            
        val event = TestActionEvent.createTestEvent(action, dataContext)
        action.update(event)
        
        assertTrue(event.presentation.isEnabledAndVisible)
    }
    
    fun testActionUnavailableForSingleParam() {
        myFixture.configureByText("a.py", """
            def fo<caret>o(a):
                pass
        """.trimIndent())
        
        val action = PyIntroduceParameterObjectAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = TestActionEvent.createTestEvent(action, dataContext)
        action.update(event)
        
        assertFalse(event.presentation.isEnabledAndVisible)
    }
}
