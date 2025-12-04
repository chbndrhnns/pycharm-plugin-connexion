package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

class PyIntroduceParameterObjectAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        if (project == null || editor == null || psiFile == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val element = psiFile.findElementAt(editor.caretModel.offset) ?: run {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (function == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }

        e.presentation.isEnabledAndVisible = parameters.size >= 2
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val element = psiFile.findElementAt(editor.caretModel.offset) ?: return
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return

        PyIntroduceParameterObjectProcessor(function).run()
    }
}
