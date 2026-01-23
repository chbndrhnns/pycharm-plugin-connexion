package com.github.chbndrhnns.betterpy.bootstrap.startup

import com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager

object PythonVersionNotifier {
    fun checkAndNotify(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            if (ProjectRootManager.getInstance(project).projectSdk == null) {
                return@runWhenSmart
            }

            if (!PythonVersionGuard.isSatisfied(project)) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("BetterPy")
                    .createNotification(
                        "Unsupported Python version",
                        "This plugin requires Python ${PythonVersionGuard.minVersionString()} or newer.",
                        NotificationType.WARNING
                    )
                    .notify(project)
            }
        }
    }
}