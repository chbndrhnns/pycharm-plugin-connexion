package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.wm.StatusBar
import java.awt.datatransfer.StringSelection

class CopyDiagnosticDataAction : AnAction() {

    companion object {
        private const val PLUGIN_ID = "com.github.chbndrhnns.intellijplatformplugincopy"

        fun getPluginVersion(): String {
            val pluginId = PluginId.getId(PLUGIN_ID)
            val plugin = PluginManagerCore.getPlugin(pluginId)
            return plugin?.version ?: "Unknown"
        }

        fun getIdeBuildNumber(): String {
            return ApplicationInfo.getInstance().build.asString()
        }

        fun formatDiagnosticData(pluginVersion: String, ideBuildNumber: String): String {
            return """
                BetterPy Plugin Version: $pluginVersion
                IDE Build Number: $ideBuildNumber
            """.trimIndent()
        }

        fun getDiagnosticData(): String {
            return formatDiagnosticData(getPluginVersion(), getIdeBuildNumber())
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val diagnosticData = getDiagnosticData()
        CopyPasteManager.getInstance().setContents(StringSelection(diagnosticData))
        StatusBar.Info.set("Diagnostic data copied to clipboard", e.project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
