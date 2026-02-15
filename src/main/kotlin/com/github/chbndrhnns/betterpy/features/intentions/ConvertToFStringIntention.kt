package com.github.chbndrhnns.betterpy.features.intentions

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStringLiteralExpression

class ConvertToFStringIntention : IntentionAction, HighPriorityAction, DumbAware {
    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Convert to f-string"

    override fun getFamilyName(): String = "Convert to f-string"

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is PyFile) return false
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableConvertToFStringIntention) return false

        val literal = findLiteral(editor, file) ?: return false
        val prefix = FStringConversionUtil.parsePrefix(literal.text) ?: return false
        if (FStringConversionUtil.shouldSkip(prefix)) return false
        return FStringConversionUtil.containsFStringPlaceholder(literal.stringValue)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        if (!PluginSettingsState.instance().state.enableConvertToFStringIntention) return

        val literal = findLiteral(editor, file) ?: return
        val prefix = FStringConversionUtil.parsePrefix(literal.text) ?: return
        if (FStringConversionUtil.shouldSkip(prefix)) return
        if (!FStringConversionUtil.containsFStringPlaceholder(literal.stringValue)) return

        WriteCommandAction.runWriteCommandAction(project, text, null, Runnable {
            FStringConversionUtil.convertToFString(literal)
        }, file)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.DIFF
    }

    private fun findLiteral(editor: Editor, file: PsiFile): PyStringLiteralExpression? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression::class.java)
    }
}
