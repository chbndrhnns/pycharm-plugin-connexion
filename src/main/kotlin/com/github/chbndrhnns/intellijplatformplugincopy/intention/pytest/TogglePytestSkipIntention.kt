package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import javax.swing.Icon

class TogglePytestSkipIntention : IntentionAction, HighPriorityAction, Iconable {

    private enum class Scope(val label: String) {
        FUNCTION("function"),
        CLASS("class"),
        MODULE("module"),
        PARAM("param"),
        PARAM_SIMPLE("param value"),
    }

    @Volatile
    private var cachedText: String = PluginConstants.ACTION_PREFIX + "Toggle pytest skip"

    override fun getText(): String = cachedText
    override fun getFamilyName(): String = "Toggle pytest skip"
    override fun getIcon(@Iconable.IconFlags flags: Int): Icon? = null
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableTogglePytestSkipIntention) return false
        if (file !is PyFile) return false

        val scope = determineScope(editor, file) ?: return false
        cachedText = PluginConstants.ACTION_PREFIX + "Toggle pytest skip (${scope.label})"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val pyCallExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java)
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        val pyFile = file as PyFile

        val toggler = PytestSkipToggler(PyElementGenerator.getInstance(project))

        if (pyCallExpression != null && (pyCallExpression.callee?.text == "pytest.param" || pyCallExpression.callee?.text == "param")) {
            toggler.toggleOnParam(pyCallExpression, pyFile)
            return
        }

        if (isSimpleParam(element)) {
            toggler.toggleOnSimpleParam(element, file)
            return
        }

        if (pyFunction != null && pyFunction.name?.startsWith("test_") == true) {
            toggler.toggleOnFunction(pyFunction, pyFile)
            return
        }

        if (pyClass != null && pyClass.name?.startsWith("Test") == true) {
            toggler.toggleOnClass(pyClass, pyFile)
            return
        }

        if (pyFile.name.startsWith("test_") || pyFile.name.endsWith("_test.py")) {
            toggler.toggleOnModule(pyFile)
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.DIFF
    }

    private fun determineScope(editor: Editor, file: PyFile): Scope? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null

        val pyCallExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java)
        if (pyCallExpression != null) {
            val callee = pyCallExpression.callee
            if (callee != null && (callee.text == "pytest.param" || callee.text == "param")) {
                return Scope.PARAM
            }
        }

        if (isSimpleParam(element)) {
            return Scope.PARAM_SIMPLE
        }

        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (pyFunction != null) {
            return if (pyFunction.name?.startsWith("test_") == true) Scope.FUNCTION else null
        }

        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        if (pyClass != null) {
            if (pyClass.name?.startsWith("Test") != true) return null
            val nameIdentifier = pyClass.nameIdentifier ?: return null
            return if (nameIdentifier.textRange.containsOffset(offset)) Scope.CLASS else null
        }

        return if (file.name.startsWith("test_") || file.name.endsWith("_test.py")) Scope.MODULE else null
    }

    private fun isSimpleParam(element: PsiElement): Boolean {
        // Handle comment case
        if (element is PsiComment) {
            var candidate: PsiElement = element
            while (candidate.parent != null && candidate.parent !is PyFile) {
                val parent = candidate.parent
                if (parent is PySequenceExpression && isPytestParametrizeOrFixtureParams(parent)) {
                    return isElementOnSeparateLine(candidate)
                }
                candidate = parent
            }
            return false
        }

        // Handle expression case
        var candidate = element
        while (candidate.parent != null && candidate.parent !is PyFile) {
            val parent = candidate.parent
            if (parent is PySequenceExpression && isPytestParametrizeOrFixtureParams(parent)) {
                // Check if candidate is NOT a pytest.param call
                if (candidate is PyCallExpression && (candidate.callee?.text == "pytest.param" || candidate.callee?.text == "param")) {
                    return false
                }

                if (candidate !is PyExpression) return false

                return isElementOnSeparateLine(candidate)
            }
            candidate = parent
        }

        return false
    }

    private fun isElementOnSeparateLine(element: PsiElement): Boolean {
        val document =
            PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile) ?: return false
        val textRange = element.textRange
        val startLine = document.getLineNumber(textRange.startOffset)
        val endLine = document.getLineNumber(textRange.endOffset)

        val lineStartOffset = document.getLineStartOffset(startLine)
        val lineEndOffset = document.getLineEndOffset(endLine)

        val textBefore = document.getText(TextRange(lineStartOffset, textRange.startOffset))
        if (textBefore.isNotBlank()) return false

        val textAfter = document.getText(TextRange(textRange.endOffset, lineEndOffset))
        val textAfterTrimmed = textAfter.trim()

        if (textAfterTrimmed.isEmpty()) return true
        if (textAfterTrimmed == ",") return true
        if (textAfterTrimmed.startsWith("#")) return true
        if (textAfterTrimmed.startsWith(",") && textAfterTrimmed.substring(1).trim().startsWith("#")) return true
        if (textAfterTrimmed.startsWith(",") && textAfterTrimmed.substring(1).trim().isEmpty()) return true

        return false
    }

}