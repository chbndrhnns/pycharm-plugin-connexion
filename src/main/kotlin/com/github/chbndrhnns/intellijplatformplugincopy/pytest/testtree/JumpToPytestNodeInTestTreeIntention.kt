package com.github.chbndrhnns.intellijplatformplugincopy.pytest.testtree

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class JumpToPytestNodeInTestTreeIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = "BetterPy: Jump to Test Tree Node"
    override fun getFamilyName(): String = "BetterPy: Jump to Test Tree Node"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val settings = PluginSettingsState.instance().state
        if (!settings.enableJumpToPytestNodeInTestTreeAction) return false

        if (!PytestTestTreeNavigator.hasActiveNonEmptyTestTree(project)) return false

        val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return false
        return PytestTestContextUtils.isInTestContext(elementAtCaret)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
        val nodeId = PytestNodeIdGenerator.fromCaretElement(elementAtCaret, project) ?: return
        PytestTestTreeNavigator.selectAndReveal(project, nodeId)
    }

    override fun startInWriteAction(): Boolean = false
}
