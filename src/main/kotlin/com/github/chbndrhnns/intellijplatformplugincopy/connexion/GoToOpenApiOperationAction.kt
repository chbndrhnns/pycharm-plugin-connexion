package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction

class GoToOpenApiOperationAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = findTargetFunction(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val function = findTargetFunction(e) ?: return
        val operations = OpenApiSpecUtil.findSpecOperationsForFunction(function)

        if (operations.isEmpty()) return

        if (operations.size == 1) {
            val op = operations.first()
            (op.operationIdElement as? com.intellij.pom.Navigatable)?.navigate(true)
        } else {
            val targets = operations.mapNotNull { it.operationIdElement as? com.intellij.psi.PsiElement }
            if (targets.isEmpty()) return

            val popup = JBPopupFactory.getInstance()
                .createPopupChooserBuilder(targets)
                .setRenderer(com.intellij.ide.util.DefaultPsiElementCellRenderer())
                .setTitle(ConnexionConstants.OPENAPI_OPERATIONS_TITLE)
                .setItemChosenCallback { (it as? com.intellij.pom.Navigatable)?.navigate(true) }
                .createPopup()
            popup.showInBestPositionFor(e.dataContext)
        }
    }

    private fun findTargetFunction(e: AnActionEvent): PyFunction? {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return null

        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return null

        return PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
    }
}
