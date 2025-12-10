package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class CopyStacktraceAction : AbstractCopyTestNodeAction("\n\n") {

    override fun update(e: AnActionEvent) {
        if (!PluginSettingsState.instance().state.enableCopyStacktraceAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        super.update(e)
    }

    override fun getValuableText(proxy: SMTestProxy, project: Project): String? {
        if (proxy.isDefect) {
            return proxy.stacktrace
        }
        return null
    }
}
