package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class SettingsToggleTest : TestBase() {

    fun testRefactoringActionDisabledWhenSettingOff() {
        withPluginSettings({ enableIntroduceCustomTypeRefactoringAction = false }) {
            myFixture.configureByText(
                "a.py",
                """
                def test_():
                    val: int = 1<caret>234
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val action = ActionManager.getInstance().getAction(
                "com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.IntroduceCustomTypeRefactoringAction"
            )
            assertNotNull("Action should be registered", action)

            val dataContext: DataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, myFixture.editor)
                .add(CommonDataKeys.PSI_FILE, myFixture.file)
                .build()
            val event = AnActionEvent.createFromDataContext("", null, dataContext)
            action.update(event)

            assertFalse("Action should be disabled when setting is off", event.presentation.isEnabled)
        }
    }

    fun testRefactoringActionEnabledWhenSettingOn() {
        withPluginSettings({ enableIntroduceCustomTypeRefactoringAction = true }) {
            myFixture.configureByText(
                "a.py",
                """
                def test_():
                    val: int = 1<caret>234
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val action = ActionManager.getInstance().getAction(
                "com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.IntroduceCustomTypeRefactoringAction"
            )
            assertNotNull("Action should be registered", action)

            val dataContext: DataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, myFixture.editor)
                .add(CommonDataKeys.PSI_FILE, myFixture.file)
                .build()
            val event = AnActionEvent.createFromDataContext("", null, dataContext)
            action.update(event)

            assertTrue("Action should be enabled when setting is on", event.presentation.isEnabled)
        }
    }
}
