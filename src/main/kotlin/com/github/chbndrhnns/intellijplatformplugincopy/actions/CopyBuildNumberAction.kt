package com.github.chbndrhnns.intellijplatformplugincopy.actions

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
}
