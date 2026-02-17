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
        val testPanel = PytestExplorerPanel(project)
        val testContent = ContentFactory.getInstance().createContent(testPanel, "Tests", false)
        testContent.setDisposer(testPanel)
        toolWindow.contentManager.addContent(testContent)

        val fixturePanel = PytestFixtureExplorerPanel(project)
        val fixtureContent = ContentFactory.getInstance().createContent(fixturePanel, "Fixtures", false)
        fixtureContent.setDisposer(fixturePanel)
        toolWindow.contentManager.addContent(fixtureContent)

        val markerPanel = PytestMarkerExplorerPanel(project)
        val markerContent = ContentFactory.getInstance().createContent(markerPanel, "Markers", false)
        markerContent.setDisposer(markerPanel)
        toolWindow.contentManager.addContent(markerContent)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return PluginSettingsState.instance().state.enablePytestExplorer
    }
}
