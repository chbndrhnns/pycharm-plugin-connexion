package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.pytest.testtree.JumpToPytestNodeInTestTreeAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import fixtures.TestBase

class JumpToPytestNodeInTestTreeActionTest : TestBase() {

    fun testUpdateRunsOnBgt() {
        val action = JumpToPytestNodeInTestTreeAction()
        assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
    }
}
