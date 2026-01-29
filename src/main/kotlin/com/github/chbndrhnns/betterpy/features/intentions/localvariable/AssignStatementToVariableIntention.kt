package com.github.chbndrhnns.betterpy.features.intentions.localvariable

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.codeInsight.intentions.PyBaseIntentionAction
import com.jetbrains.python.psi.*
import javax.swing.Icon

class AssignStatementToVariableIntention : PyBaseIntentionAction(), PriorityAction, Iconable {

    override fun getFamilyName(): String = "Assign statement to variable"

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Assign statement to variable"

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun getIcon(flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableAssignStatementToVariableIntention) return false
        if (file !is PyFile) return false

        val statement = getExpressionStatementAtCaret(editor, file) ?: return false
        return statement.expression is PyCallExpression
    }

    @Throws(IncorrectOperationException::class)
    override fun doInvoke(project: Project, editor: Editor, file: PsiFile) {
        val statement = getExpressionStatementAtCaret(editor, file) ?: return
        val expression = statement.expression as? PyCallExpression ?: return
        val languageLevel = LanguageLevel.forElement(file)
        val generator = PyElementGenerator.getInstance(project)

        val scope = PsiTreeUtil.getParentOfType(statement, PyFunction::class.java, PyFile::class.java) ?: return
        val usedNames = collectUsedNames(scope)
        val variableName = uniqueName("result", usedNames)

        val assignment = generator.createFromText(
            languageLevel,
            PyAssignmentStatement::class.java,
            "$variableName = ${expression.text}"
        )
        statement.replace(assignment)
    }

    private fun getExpressionStatementAtCaret(editor: Editor, file: PsiFile): PyExpressionStatement? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PyExpressionStatement::class.java)
    }

    private fun collectUsedNames(scope: PsiElement): Set<String> {
        val used = mutableSetOf<String>()
        PsiTreeUtil.collectElementsOfType(scope, PyTargetExpression::class.java)
            .mapNotNullTo(used) { it.name }
        PsiTreeUtil.collectElementsOfType(scope, PyNamedParameter::class.java)
            .mapNotNullTo(used) { it.name }
        return used
    }

    private fun uniqueName(baseName: String, usedNames: Set<String>): String {
        if (baseName !in usedNames) return baseName
        var index = 1
        var candidate = "$baseName$index"
        while (candidate in usedNames) {
            index += 1
            candidate = "$baseName$index"
        }
        return candidate
    }
}
