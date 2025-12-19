package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class ReplaceExpectedWithActualIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Use actual test outcome"
    override fun getFamilyName(): String = "Use actual test outcome"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val useCase = UseActualOutcomeUseCase(TestOutcomeDiffService.getInstance(project))
        return useCase.isAvailable(project, editor, file)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        invokeWithTestKey(project, editor, file, testKey = null)
    }

    fun invokeWithTestKey(project: Project, editor: Editor, file: PsiFile, testKey: String?) {
        val useCase = UseActualOutcomeUseCase(TestOutcomeDiffService.getInstance(project))
        useCase.invoke(project, editor, file, testKey)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF
}
