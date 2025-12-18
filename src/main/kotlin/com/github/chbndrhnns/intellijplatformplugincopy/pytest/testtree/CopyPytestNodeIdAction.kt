package com.github.chbndrhnns.intellijplatformplugincopy.pytest.testtree

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class CopyPytestNodeIdAction : AbstractCopyTestNodeAction("\n") {

    override fun update(e: AnActionEvent) {
        if (!PluginSettingsState.instance().state.enableCopyPytestNodeIdsAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        super.update(e)
    }

    override fun getValuableText(proxy: SMTestProxy, project: Project): String? {
        val nodeId = PytestNodeIdGenerator.parseProxy(proxy, project)
        return nodeId?.nodeid
    }

    override fun processResult(result: List<String>): List<String> {
        return result.sorted()
    }
}
