package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.pytest.testtree.PytestNodeIdGenerator
import com.github.chbndrhnns.intellijplatformplugincopy.pytest.testtree.PytestTestContextUtils
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import java.awt.datatransfer.StringSelection

class CopyPytestNodeIdFromEditorAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        if (project == null || editor == null || psiFile == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val settings = PluginSettingsState.instance().state
        if (!settings.enableCopyPytestNodeIdFromEditorAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset)
        if (elementAtCaret == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // Must be in a test context
        if (!PytestTestContextUtils.isInTestContext(elementAtCaret)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // Must be on a test method
        // PytestTestContextUtils.isInTestContext checks if we are in a test file AND (test function OR test class).
        // But the requirement says "we are on a test method".
        // So let's check explicitly if we are inside a test function.
        val function = PsiTreeUtil.getParentOfType(elementAtCaret, PyFunction::class.java, false)
        if (function == null || !PytestTestContextUtils.isTestFunction(function)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset) ?: return

        val nodeId = PytestNodeIdGenerator.fromCaretElement(elementAtCaret, project) ?: return

        CopyPasteManager.getInstance().setContents(StringSelection(nodeId))
    }
}
