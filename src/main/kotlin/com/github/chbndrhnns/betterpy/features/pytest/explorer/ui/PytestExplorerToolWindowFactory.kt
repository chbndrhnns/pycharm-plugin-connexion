package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class PytestExplorerToolWindowFactory : ToolWindowFactory, DumbAware {

    private val LOG = Logger.getInstance(PytestExplorerToolWindowFactory::class.java)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        LOG.info("Creating Pytest Explorer tool window for project: ${project.name}")
        val panel = PytestExplorerPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return PluginSettingsState.instance().state.enablePytestExplorer
    }
}
