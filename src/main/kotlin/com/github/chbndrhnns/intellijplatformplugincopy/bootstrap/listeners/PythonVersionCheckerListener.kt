package com.github.chbndrhnns.intellijplatformplugincopy.bootstrap.listeners

import com.github.chbndrhnns.intellijplatformplugincopy.bootstrap.startup.PythonVersionNotifier
import com.github.chbndrhnns.intellijplatformplugincopy.features.statusbar.BetterPyStatusBarWidget
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.wm.WindowManager

class PythonVersionCheckerListener(private val project: Project) : ModuleRootListener {
    override fun rootsChanged(event: ModuleRootEvent) {
        PythonVersionNotifier.checkAndNotify(project)
        WindowManager.getInstance().getStatusBar(project)?.updateWidget(BetterPyStatusBarWidget.ID)
    }
}
