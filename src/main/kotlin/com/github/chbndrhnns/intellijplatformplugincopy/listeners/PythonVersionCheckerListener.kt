package com.github.chbndrhnns.intellijplatformplugincopy.listeners

import com.github.chbndrhnns.intellijplatformplugincopy.startup.PythonVersionNotifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener

class PythonVersionCheckerListener(private val project: Project) : ModuleRootListener {
    override fun rootsChanged(event: ModuleRootEvent) {
        PythonVersionNotifier.checkAndNotify(project)
    }
}
