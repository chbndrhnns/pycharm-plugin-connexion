package com.github.chbndrhnns.betterpy.features.intentions.annotations

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.psi.PyFunction

class CopyMethodAnnotationsFromParentIntention : PsiElementBaseIntentionAction(), PriorityAction {
    private var actionText: String =
        PluginConstants.ACTION_PREFIX + "Copy type annotations from parent"

    override fun getFamilyName(): String = "Copy type annotations from parent"

    override fun getText(): String = actionText

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!PluginSettingsState.instance().state.enableCopyMethodAnnotationsFromParentIntention) return false
        if (!element.isOwnCode()) return false

        val function = element.parentOfType<PyFunction>() ?: return false
        val nameId = function.nameIdentifier
        if (nameId == null || !PsiTreeUtil.isAncestor(nameId, element, false)) return false
        val plan = CopyTypeAnnotationsFromParentSupport.buildCopyPlan(function) ?: return false
        actionText = if (plan.source == function) {
            PluginConstants.ACTION_PREFIX + "Copy type annotations to subclasses"
        } else {
            PluginConstants.ACTION_PREFIX + "Copy type annotations from parent"
        }
        return plan.hasChanges
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = element.parentOfType<PyFunction>() ?: return
        val plan = CopyTypeAnnotationsFromParentSupport.buildCopyPlan(function) ?: return

        if (!plan.conflicts.isEmpty) {
            CopyTypeAnnotationsFromParentSupport.showConflictsDialog(project, plan.conflicts, text)
            return
        }

        WriteCommandAction.runWriteCommandAction(project, text, null, {
            CopyTypeAnnotationsFromParentSupport.applyPlan(project, plan)
        }, function.containingFile)
    }
}
