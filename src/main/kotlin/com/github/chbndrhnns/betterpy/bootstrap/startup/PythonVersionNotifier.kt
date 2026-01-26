package com.github.chbndrhnns.betterpy.bootstrap.startup

import com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicLong

object PythonVersionNotifier {
    private val lastNotificationAt = AtomicLong(0L)
    private const val MIN_NOTIFICATION_INTERVAL_MS = 60_000L

    @Suppress("DialogTitleCapitalization")
    fun checkAndNotify(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            if (!PythonVersionGuard.hasPythonSdk(project)) {
                return@runWhenSmart
            }

            if (!PythonVersionGuard.isSatisfied(project) && shouldNotify()) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("BetterPy")
                    .createNotification(
                        "BetterPy: Unsupported Python version",
                        "This plugin requires Python ${PythonVersionGuard.minVersionString()} or newer.",
                        NotificationType.WARNING
                    )
                    .notify(project)
            }
        }
    }

    private fun shouldNotify(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastNotificationAt.get()
        if (now - last < MIN_NOTIFICATION_INTERVAL_MS) {
            return false
        }
        return lastNotificationAt.compareAndSet(last, now)
    }
}
