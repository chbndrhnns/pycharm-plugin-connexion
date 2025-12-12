package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import javax.swing.Icon

class TogglePytestSkipIntention : IntentionAction, HighPriorityAction, Iconable {

    private enum class Scope(val label: String) {
        FUNCTION("function"),
        CLASS("class"),
        MODULE("module"),
    }

    @Volatile
    private var cachedText: String = "Toggle pytest skip"

    override fun getText(): String = cachedText
    override fun getFamilyName(): String = "Toggle pytest skip"
    override fun getIcon(@Iconable.IconFlags flags: Int): Icon? = null
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableTogglePytestSkipIntention) return false
        if (file !is PyFile) return false

        val scope = determineScope(editor, file) ?: return false
        cachedText = "Toggle pytest skip (${scope.label})"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        val pyFile = file as PyFile

        val toggler = PytestSkipToggler(PyElementGenerator.getInstance(project))

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

}