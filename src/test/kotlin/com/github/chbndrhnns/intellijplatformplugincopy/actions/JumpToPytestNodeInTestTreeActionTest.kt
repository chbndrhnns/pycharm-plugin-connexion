package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import fixtures.TestBase

class JumpToPytestNodeInTestTreeActionTest : TestBase() {

    fun testUpdateRunsOnBgt() {
        val action = JumpToPytestNodeInTestTreeAction()
        assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
    }
}
