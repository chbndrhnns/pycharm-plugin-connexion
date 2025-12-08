package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.wm.StatusBar
import java.awt.datatransfer.StringSelection

class CopyBuildNumberAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val buildNumber = ApplicationInfo.getInstance().build.asString()
        CopyPasteManager.getInstance().setContents(StringSelection(buildNumber))
        StatusBar.Info.set("Build number copied: $buildNumber", e.project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        // Hide when project Python version is below minimum
        val ok = PythonVersionGuard.isSatisfied(project)
        e.presentation.isEnabledAndVisible = ok
    }
}
