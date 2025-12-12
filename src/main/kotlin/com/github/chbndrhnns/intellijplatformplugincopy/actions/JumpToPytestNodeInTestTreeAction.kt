package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction

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

        val isOnTestTarget = ReadAction.compute<Boolean, RuntimeException> {
            val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset)
            val function = elementAtCaret?.let { PsiTreeUtil.getParentOfType(it, PyFunction::class.java, false) }
            val clazz = elementAtCaret?.let { PsiTreeUtil.getParentOfType(it, PyClass::class.java, false) }
            function != null || clazz != null
        }

        e.presentation.isEnabledAndVisible = isOnTestTarget
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
