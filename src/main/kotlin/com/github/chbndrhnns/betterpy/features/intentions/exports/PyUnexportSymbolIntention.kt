package com.github.chbndrhnns.betterpy.features.intentions.exports

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*

class PyUnexportSymbolIntention : IntentionAction, HighPriorityAction {
    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Remove from __all__"
    override fun getFamilyName(): String = "Remove from __all__"

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is PyFile) return false
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableUnexportSymbolIntention) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val stringLiteral = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression::class.java) ?: return false
        val list = PsiTreeUtil.getParentOfType(stringLiteral, PySequenceExpression::class.java) ?: return false
        val assignment = PsiTreeUtil.getParentOfType(list, PyAssignmentStatement::class.java) ?: return false

        return assignment.targets.any { (it as? PyTargetExpression)?.name == PyNames.ALL }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val stringLiteral = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression::class.java) ?: return
        val name = stringLiteral.stringValue

        val usages = findUsages(file as PyFile, name)
        if (usages.isNotEmpty() && !ApplicationManager.getApplication().isUnitTestMode) {
            val result = Messages.showYesNoDialog(
                project,
                "The symbol '$name' is used in the project. Are you sure you want to remove it from __all__?",
                "Remove from __all__",
                Messages.getQuestionIcon()
            )
            if (result != Messages.YES) return
        }

        WriteCommandAction.runWriteCommandAction(project, "Remove from __all__", null, {
            // Remove from __all__
            stringLiteral.delete()

            // Remove corresponding import
            removeImport(file, name)
        })
    }

    private fun findUsages(file: PyFile, name: String): Collection<PsiFile> {
        val project = file.project
        val scope = GlobalSearchScope.projectScope(project)

        val symbol = file.findTopLevelAttribute(name)
            ?: file.findTopLevelFunction(name)
            ?: file.findTopLevelClass(name)
            ?: findImportedSymbol(file, name)
            ?: return emptyList()

        // We are interested in usages OUTSIDE of the file where the symbol is defined or re-exported.
        // Actually, if it's re-exported in __init__.py, we care about usages of it via that __init__.py.
        // But ReferencesSearch.search(symbol) finds all references to that symbol.

        return ReferencesSearch.search(symbol, scope).mapNotNull { it.element.containingFile }
            .filter { it != file && it.isOwnCode() }.distinct()
    }

    private fun findImportedSymbol(file: PyFile, name: String): PyElement? {
        for (importElement in file.importTargets) {
            if (importElement.visibleName == name) return importElement
        }
        for (fromImport in file.fromImports) {
            for (importElement in fromImport.importElements) {
                if (importElement.visibleName == name) return importElement
            }
        }
        return null
    }

    private fun removeImport(file: PyFile, name: String) {
        for (fromImport in file.fromImports) {
            val elements = fromImport.importElements
            val match = elements.find { it.visibleName == name }
            if (match != null) {
                if (elements.size == 1) {
                    fromImport.delete()
                } else {
                    match.delete()
                }
                return
            }
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }
}
