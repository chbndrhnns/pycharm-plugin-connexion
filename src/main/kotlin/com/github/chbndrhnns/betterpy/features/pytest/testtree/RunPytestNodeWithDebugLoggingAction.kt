package com.github.chbndrhnns.betterpy.features.pytest.testtree

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.testframework.TestTreeView
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.python.testing.PyAbstractTestConfiguration
import com.jetbrains.python.testing.PyTestConfiguration
import javax.swing.tree.DefaultMutableTreeNode

class RunPytestNodeWithDebugLoggingAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        if (!PluginSettingsState.instance().state.enableRunWithDebugLoggingFromTestTreeAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? TestTreeView
        val project = e.project
        if (view == null || project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val hasConfiguration = RunManager.getInstance(project).selectedConfiguration
            ?.configuration is PyAbstractTestConfiguration
        if (!hasConfiguration) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val hasNodeId = ReadAction.compute<Boolean, RuntimeException> {
            resolveSelectedNodeId(view, project) != null
        }

        e.presentation.isEnabledAndVisible = hasNodeId
    }

    override fun actionPerformed(e: AnActionEvent) {
        val view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? TestTreeView
        val project = e.project
        if (view == null || project == null) {
            LOG.info("Run with debug action invoked without test tree view or project.")
            return
        }

        val runManager = RunManager.getInstance(project)
        val originalSettings = runManager.selectedConfiguration
        if (originalSettings == null) {
            LOG.info("No selected run configuration to duplicate for debug run.")
            return
        }

        val nodeId = ReadAction.compute<String?, RuntimeException> {
            resolveSelectedNodeId(view, project)
        }

        val debugSettings = PytestDebugLoggingConfigurationDuplicator.duplicate(
            runManager = runManager,
            originalSettings = originalSettings,
            nodeId = nodeId
        ) ?: return

        val executor = resolveExecutor(e)
        ProgramRunnerUtil.executeConfiguration(debugSettings, executor)
    }

    private fun resolveExecutor(event: AnActionEvent): Executor {
        val executor = event.getData(EXECUTOR_DATA_KEY)
        return executor ?: DefaultRunExecutor.getRunExecutorInstance()
    }

    private fun resolveSelectedNodeId(view: TestTreeView, project: Project): String? {
        val selectionPaths = view.selectionPaths ?: return null
        for (path in selectionPaths) {
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val proxy = TestProxyExtractor.getTestProxy(node) ?: continue
            val record = PytestNodeIdGenerator.parseProxy(proxy, project) ?: continue
            return record.nodeid
        }
        return null
    }

    companion object {
        private val LOG = Logger.getInstance(RunPytestNodeWithDebugLoggingAction::class.java)
        private val EXECUTOR_DATA_KEY = DataKey.create<Executor>("Executor")
    }
}

object PytestDebugLoggingConfigurationDuplicator {
    private val LOG = Logger.getInstance(PytestDebugLoggingConfigurationDuplicator::class.java)
    private const val DEBUG_SUFFIX = " (debug)"
    private const val CLI_LOG_LEVEL_ARGUMENT = "--log-cli-level=DEBUG"

    fun duplicate(
        runManager: RunManager,
        originalSettings: RunnerAndConfigurationSettings,
        nodeId: String?
    ): RunnerAndConfigurationSettings? {
        val originalConfig = originalSettings.configuration
        if (originalConfig !is PyAbstractTestConfiguration) {
            LOG.info("Selected configuration is not a PyTest configuration: ${originalConfig::class.java.name}.")
            return null
        }

        val clonedConfig = originalConfig.clone() as PyAbstractTestConfiguration
        clonedConfig.name = buildDebugName(originalSettings.name)

        if (!nodeId.isNullOrBlank() && clonedConfig is PyTestConfiguration) {
            clonedConfig.parameters = appendArgument(clonedConfig.parameters, nodeId)
            val keyword = extractKeyword(nodeId)
            if (keyword.isNotBlank()) {
                clonedConfig.keywords = appendKeyword(clonedConfig.keywords, keyword)
            }
        }

        clonedConfig.additionalArguments = appendArgument(clonedConfig.additionalArguments, CLI_LOG_LEVEL_ARGUMENT)

        val newSettings = runManager.createConfiguration(clonedConfig, originalSettings.factory)
        newSettings.isTemporary = true
        runManager.addConfiguration(newSettings)
        runManager.selectedConfiguration = newSettings

        LOG.info("Created debug pytest configuration '${newSettings.name}' from '${originalSettings.name}'.")
        return newSettings
    }

    internal fun appendArgument(existing: String?, addition: String): String {
        val current = existing?.trim().orEmpty()
        if (current.isBlank()) return addition
        if (current.contains(addition)) return current
        return "$current $addition"
    }

    internal fun appendKeyword(existing: String?, addition: String): String {
        val current = existing?.trim().orEmpty()
        if (current.isBlank()) return addition
        if (current.contains(addition)) return current
        return "$current and $addition"
    }

    internal fun buildDebugName(originalName: String): String = "$originalName$DEBUG_SUFFIX"

    internal fun extractKeyword(nodeId: String): String {
        return nodeId.substringAfterLast("::").substringBefore("[")
    }
}