package com.github.chbndrhnns.intellijplatformplugincopy.intention.signature

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

class StripSignatureTypeAnnotationsIntention : IntentionAction, HighPriorityAction, DumbAware {

    override fun getText(): String = "Strip type annotations from signature"

    override fun getFamilyName(): String = "Strip signature type annotations"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableStripSignatureTypeAnnotationsIntention) return false
        if (file !is PyFile) return false

        val fn = findFunctionAtCaret(editor, file) ?: return false
        return hasAnySignatureAnnotation(fn)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (!PluginSettingsState.instance().state.enableStripSignatureTypeAnnotationsIntention) return
        val pyFile = file as? PyFile ?: return
        val fn = findFunctionAtCaret(editor, pyFile) ?: return
        if (!hasAnySignatureAnnotation(fn)) return

        WriteCommandAction.runWriteCommandAction(project, text, null, Runnable {
            stripAnnotations(fn)
            CodeStyleManager.getInstance(project).reformat(pyFile)
        }, pyFile)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    override fun startInWriteAction(): Boolean = false

    private fun findFunctionAtCaret(editor: Editor, file: PsiFile): PyFunction? {
        val offset = editor.caretModel.offset
        val leaf = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(leaf, PyFunction::class.java)
    }

    private fun hasAnySignatureAnnotation(fn: PyFunction): Boolean {
        if (fn.annotation != null) return true
        return fn.parameterList.parameters.asSequence()
            .filterIsInstance<PyNamedParameter>()
            .any { it.annotation != null }
    }

    private fun stripAnnotations(fn: PyFunction) {
        // Return annotation
        fn.annotation?.let { ann: PyAnnotation ->
            ann.delete()
        }

        // Parameter annotations
        fn.parameterList.parameters
            .asSequence()
            .filterIsInstance<PyNamedParameter>()
            .forEach { p ->
                p.annotation?.delete()
            }
    }
}
