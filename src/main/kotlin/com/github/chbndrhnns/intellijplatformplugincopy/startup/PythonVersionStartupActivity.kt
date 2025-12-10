package com.github.chbndrhnns.intellijplatformplugincopy.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class PythonVersionStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        PythonVersionNotifier.checkAndNotify(project)
    }
}