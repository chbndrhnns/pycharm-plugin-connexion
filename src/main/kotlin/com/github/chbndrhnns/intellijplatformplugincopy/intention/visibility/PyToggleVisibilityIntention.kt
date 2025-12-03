package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PythonUiService
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import javax.swing.Icon

/**
 * Shared utilities for visibility intentions working on Python symbols.
 */
abstract class PyToggleVisibilityIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    // Do NOT start in a write action: refactorings must be invoked outside write actions
    // (BaseRefactoringProcessor will manage write actions internally). Starting inside
    // a write action leads to: "Refactorings should not be started inside write action".
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val symbol = findTargetSymbol(editor, file) ?: return false
        val name = symbol.name ?: return false
        if (isDunder(name)) return false
        return isAvailableForName(name)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val symbol = findTargetSymbol(editor, file) ?: return
        val name = symbol.name ?: return
        val newName = calcNewName(name) ?: return
        performRename(project, symbol, newName)
    }

    protected open fun findTargetSymbol(editor: Editor, file: PsiFile): PsiNamedElement? {
        val offset = editor.caretModel.offset
        val atCaret = file.findElementAt(offset) ?: return null
        val named = PsiTreeUtil.getParentOfType(
            atCaret,
            PyFunction::class.java,
            PyClass::class.java,
            PyTargetExpression::class.java
        ) as? PsiNamedElement
        return named
    }

    protected fun isDunder(name: String): Boolean = name.length >= 4 && name.startsWith("__") && name.endsWith("__")

    /** Return true when intention should be shown for [name]. */
    protected abstract fun isAvailableForName(name: String): Boolean

    /** Calculate new name for refactoring, or null to skip. */
    protected abstract fun calcNewName(name: String): String?

    /** Perform rename using Python refactoring UI service. Split out for testing/mocking if needed. */
    protected open fun performRename(project: Project, element: PsiNamedElement, newName: String) {
        // Intention Preview runs on a background thread and works on a non-physical copy.
        // Running refactoring processors there violates threading rules and may touch UI.
        // In preview mode, update the preview PSI directly instead of invoking the rename processor.
        if (IntentionPreviewUtils.isIntentionPreviewActive() || IntentionPreviewUtils.isPreviewElement(element)) {
            // Rename the preview PSI under a write command to satisfy PSI change requirements
            WriteCommandAction.runWriteCommandAction(project) {
                element.setName(newName)
            }
            return
        }

        // In regular execution, use Python UI service to run a proper RenameProcessor that updates imports/usages
        PythonUiService.getInstance()
            .runRenameProcessor(project, element, newName, false, false)
    }
}
