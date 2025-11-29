package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CopyPackageContentActionTest : BasePlatformTestCase() {

    fun testActionUpdateThread() {
        val action = CopyPackageContentAction()
        assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
    }

    fun testUpdateRespectsSetting() {
        val action = CopyPackageContentAction()
        val directory = myFixture.tempDirFixture.findOrCreateDir("testDir")

        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, directory)
            .build()

        val event = TestActionEvent.createTestEvent(action, dataContext)

        // 1. Enabled by default
        val settings = PluginSettingsState.instance().state
        assertTrue("Default setting should be true", settings.enableCopyPackageContentAction)

        action.update(event)
        assertTrue("Action should be enabled when setting is true", event.presentation.isEnabledAndVisible)

        // 2. Disabled
        settings.enableCopyPackageContentAction = false
        try {
            action.update(event)
            assertFalse("Action should be disabled when setting is false", event.presentation.isEnabledAndVisible)
        } finally {
            // Restore setting
            settings.enableCopyPackageContentAction = true
        }
    }

    fun testSingleFileSupport() {
        val action = CopyPackageContentAction()
        val file = myFixture.addFileToProject("singleFile.txt", "Hello World")

        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file.virtualFile)
            .build()

        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.update(event)
        assertTrue("Action should be enabled for single file", event.presentation.isEnabledAndVisible)
    }
}
