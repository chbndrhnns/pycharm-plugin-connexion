package com.github.chbndrhnns.betterpy.features.actions

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import java.awt.datatransfer.StringSelection

class CopyAsImportStatementAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        if (project == null || editor == null || psiFile == null || psiFile !is PyFile) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val settings = PluginSettingsState.instance().state
        if (!settings.enableCopyAsImportStatementAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val symbol = findTopLevelSymbol(psiFile, editor.caretModel.offset)
        e.presentation.isEnabledAndVisible = symbol != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? PyFile ?: return

        val symbol = findTopLevelSymbol(psiFile, editor.caretModel.offset) ?: return
        val symbolName = when (symbol) {
            is PyFunction -> symbol.name
            is PyClass -> symbol.name
            else -> null
        } ?: return

        val qName = QualifiedNameFinder.findCanonicalImportPath(symbol, null)
            ?: return

        val importStatement = "from $qName import $symbolName"
        CopyPasteManager.getInstance().setContents(StringSelection(importStatement))
    }

    private fun findTopLevelSymbol(pyFile: PyFile, offset: Int): com.intellij.psi.PsiElement? {
        val element = pyFile.findElementAt(offset) ?: return null

        // Find the nearest enclosing function or class
        var current = element
        while (current != null && current !is PyFile) {
            if (current is PyFunction || current is PyClass) {
                // Only return if it's a direct child of the file (top-level)
                return if (current.parent is PyFile) current else null
            }
            current = current.parent
        }
        return null
    }
}
