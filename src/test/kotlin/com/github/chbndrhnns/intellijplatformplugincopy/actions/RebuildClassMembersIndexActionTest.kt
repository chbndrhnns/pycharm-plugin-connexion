package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.openapi.actionSystem.ActionManager
import fixtures.TestBase

class RebuildClassMembersIndexActionTest : TestBase() {

    fun testActionIsRegistered() {
        val actionManager = ActionManager.getInstance()
        val action =
            actionManager.getAction("com.github.chbndrhnns.intellijplatformplugincopy.actions.RebuildClassMembersIndexAction")
        assertNotNull("Action should be registered in plugin.xml", action)
        assertTrue(
            "Registered action should be RebuildClassMembersIndexAction",
            action is RebuildClassMembersIndexAction
        )
    }
}
