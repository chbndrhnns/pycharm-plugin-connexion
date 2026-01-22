package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.testtree

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction

class JumpToPytestNodeInTestTreeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val settings = PluginSettingsState.instance().state
        if (!settings.enableJumpToPytestNodeInTestTreeAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        if (project == null || editor == null || psiFile == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // Action is only visible if we have an active test tree
        if (!PytestTestTreeNavigator.hasActiveNonEmptyTestTree(project)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val isInTestContext = ReadAction.compute<Boolean, RuntimeException> {
            val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset)
            elementAtCaret != null && PytestTestContextUtils.isInTestContext(elementAtCaret)
        }

        e.presentation.isEnabledAndVisible = isInTestContext
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val nodeId = ReadAction.compute<String?, RuntimeException> {
            val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset) ?: return@compute null
            PytestNodeIdGenerator.fromCaretElement(elementAtCaret, project)
        } ?: return

        PytestTestTreeNavigator.selectAndReveal(project, nodeId)
    }
}
