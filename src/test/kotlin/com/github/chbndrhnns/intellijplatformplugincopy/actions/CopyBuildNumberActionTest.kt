package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.openapi.actionSystem.ActionManager
import fixtures.TestBase

class CopyBuildNumberActionTest : TestBase() {

    fun testActionIsRegistered() {
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction("com.github.chbndrhnns.intellijplatformplugincopy.actions.CopyBuildNumberAction")
        assertNotNull("Action should be registered in plugin.xml", action)
        assertTrue("Registered action should be CopyBuildNumberAction",
            action is CopyBuildNumberAction)
    }
}
