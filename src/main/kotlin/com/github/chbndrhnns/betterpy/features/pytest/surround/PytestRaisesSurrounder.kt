package com.github.chbndrhnns.betterpy.features.pytest.surround

import com.github.chbndrhnns.betterpy.core.pytest.PytestParametrizeUtil
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.pytest.testtree.PytestTestContextUtils
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class PytestRaisesSurrounder : Surrounder {

    override fun getTemplateDescription(): String = "pytest.raises()"

    override fun isApplicable(elements: Array<PsiElement>): Boolean {
        if (!PluginSettingsState.instance().state.enableSurroundWithPytestRaisesIntention) return false
        if (elements.isEmpty()) return false

        val statements = elements.mapNotNull { it as? PyStatement }
        if (statements.size != elements.size) return false

        val statementList = statements.first().parent as? PyStatementList ?: return false
        if (statements.any { it.parent != statementList }) return false

        val file = statements.first().containingFile as? PyFile ?: return false
        val function = PsiTreeUtil.getParentOfType(statements.first(), PyFunction::class.java, false) ?: return false
        if (!PytestTestContextUtils.isTestFunction(function)) return false

        if (statements.any { isInsidePytestRaises(it) }) return false

        return true
    }

    override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
        val statements = elements.mapNotNull { it as? PyStatement }
        if (statements.isEmpty()) return null

        val file = statements.first().containingFile as? PyFile ?: return null
        PytestParametrizeUtil.ensurePytestImported(file)

        val document = editor.document
        val documentManager = PsiDocumentManager.getInstance(project)
        documentManager.doPostponedOperationsAndUnblockDocument(document)
        val startOffset = statements.first().textRange.startOffset
        val endOffset = statements.last().textRange.endOffset

        val startLine = document.getLineNumber(startOffset)
        val lineStartOffset = document.getLineStartOffset(startLine)
        val linePrefix = document.getText(TextRange(lineStartOffset, startOffset))
        val baseIndent = linePrefix.takeWhile { it == ' ' || it == '\t' }
        val innerIndent = baseIndent + "    "

        val blockText = statements.joinToString("\n") { it.text }
        val indentedBlock = blockText.lines().joinToString("\n") { line ->
            if (line.isNotBlank()) innerIndent + line else line
        }

        val newText = "with pytest.raises(Exception):\n$indentedBlock"
        document.replaceString(startOffset, endOffset, newText)
        documentManager.commitDocument(document)

        return TextRange(startOffset, startOffset + newText.length)
    }

    private fun isInsidePytestRaises(statement: PyStatement): Boolean {
        var current: PyWithStatement? = PsiTreeUtil.getParentOfType(statement, PyWithStatement::class.java, false)
        while (current != null) {
            if (current.withItems.any { isPytestRaisesCall(it.expression) }) return true
            current = PsiTreeUtil.getParentOfType(current, PyWithStatement::class.java, true)
        }
        return false
    }

    private fun isPytestRaisesCall(expression: PyExpression?): Boolean {
        val call = expression as? PyCallExpression ?: return false
        val callee = call.callee as? PyQualifiedExpression ?: return false
        val qualifiedName = callee.asQualifiedName()?.toString()
        if (qualifiedName == "pytest.raises" || qualifiedName?.endsWith(".pytest.raises") == true) return true
        return callee.name == "raises"
    }

}
