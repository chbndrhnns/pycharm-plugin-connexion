package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class CopyFQNAction : AbstractCopyTestNodeAction("\n") {

    override fun update(e: AnActionEvent) {
        if (!PluginSettingsState.instance().state.enableCopyFQNsAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        super.update(e)
    }

    override fun getValuableText(proxy: SMTestProxy, project: Project): String? {
        return generateFQN(proxy, project)
    }

    override fun processResult(result: List<String>): List<String> {
        return result.distinct().sorted()
    }

    internal fun generateFQN(proxy: SMTestProxy, project: Project): String? {
        val url = proxy.locationUrl
        if (url != null && url.startsWith("python_uttestid://")) {
            return stripParameters(url.removePrefix("python_uttestid://"))
        }

        val nodeId = PytestNodeIdGenerator.parseProxy(proxy, project) ?: return null
        // Transform "path/to/file.py::Class::method" to "path.to.file.Class.method"
        val fqn = nodeId.nodeid.replace(".py", "")
            .replace("/", ".")
            .replace("::", ".")
        return stripParameters(fqn)
    }

    private fun stripParameters(fqn: String): String {
        if (fqn.endsWith("]")) {
            val index = fqn.lastIndexOf('[')
            if (index != -1) {
                return fqn.substring(0, index)
            }
        }
        return fqn
    }
}
