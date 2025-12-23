package com.github.chbndrhnns.intellijplatformplugincopy.run

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.run.PythonConfigurationType
import com.jetbrains.python.run.PythonRunConfiguration

class PyPackageRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        if (!PluginSettingsState.instance().state.enablePyPackageRunConfigurationAction) return null

        if (element.node.elementType != PyTokenTypes.IF_KEYWORD) return null

        val ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement::class.java) ?: return null
        val condition = ifStatement.ifPart.condition as? PyBinaryExpression ?: return null

        if (condition.operator != PyTokenTypes.EQEQ) return null

        val left = condition.leftExpression as? PyReferenceExpression ?: return null
        if (left.referencedName != "__name__") return null

        val right = condition.rightExpression ?: return null
        if (right.text.trim('\'', '"') != "__main__") return null

        val file = element.containingFile as? PyFile ?: return null
        val qName = QualifiedNameFinder.findShortestImportableQName(file)?.toString()
            ?: file.name.removeSuffix(".py")

        val action = object : AnAction(
            PluginConstants.ACTION_PREFIX + "Run as module '${qName}'",
            "Creates a run configuration for this module using the package name",
            AllIcons.Actions.Execute
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                createAndRunConfiguration(project, qName)
            }
        }

        return Info(action)
    }

    private fun createAndRunConfiguration(project: Project, moduleName: String) {
        val runManager = RunManager.getInstance(project)
        val type = PythonConfigurationType.getInstance()
        val settings: RunnerAndConfigurationSettings = runManager.createConfiguration(moduleName, type.factory)
        val configuration = settings.configuration as PythonRunConfiguration

        configuration.scriptName = moduleName
        configuration.isModuleMode = true

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings)
        if (builder != null) {
            ExecutionManager.getInstance(project).restartRunProfile(builder.build())
        }
    }
}
