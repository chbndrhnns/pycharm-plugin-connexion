package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.pytest.testtree.PytestTestContextUtils
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
import com.jetbrains.python.PyTokenTypes
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
        if (!file.isOwnCode()) return false
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

        if (pyFunction != null && PytestTestContextUtils.isTestFunction(pyFunction)) {
            toggler.toggleOnFunction(pyFunction, pyFile)
            return
        }

        if (pyClass != null && PytestTestContextUtils.isTestClass(pyClass)) {
            toggler.toggleOnClass(pyClass, pyFile)
            return
        }

        if (PytestTestContextUtils.isTestFile(pyFile)) {
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
            if (!PytestTestContextUtils.isTestFunction(pyFunction)) return null
            if (element.node.elementType == PyTokenTypes.DEF_KEYWORD) return Scope.FUNCTION
            return Scope.FUNCTION
        }

        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        if (pyClass != null) {
            if (!PytestTestContextUtils.isTestClass(pyClass)) return null
            val nameIdentifier = pyClass.nameIdentifier
            if (nameIdentifier != null && nameIdentifier.textRange.containsOffset(offset)) return Scope.CLASS
            if (element.node.elementType == PyTokenTypes.CLASS_KEYWORD) return Scope.CLASS
            return null
        }

        if (!PytestTestContextUtils.isTestFile(file)) return null

        val assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement::class.java)
        if (assignment != null && assignment.targets.any { it.text == "pytestmark" }) {
            return Scope.MODULE
        }

        val statement = PsiTreeUtil.getParentOfType(element, PyStatement::class.java)
        if (statement != null) {
            return null
        }

        return Scope.MODULE
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