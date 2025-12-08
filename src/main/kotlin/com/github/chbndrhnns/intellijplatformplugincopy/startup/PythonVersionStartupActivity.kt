package com.github.chbndrhnns.intellijplatformplugincopy.startup

import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class PythonVersionStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!PythonVersionGuard.isSatisfied(project)) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Python DDD Toolkit")
                .createNotification(
                    "Unsupported Python version",
                    "This plugin requires Python ${PythonVersionGuard.minVersionString()} or newer.",
                    NotificationType.WARNING
                )
                .notify(project)
        }
    }
}
