package com.github.chbndrhnns.intellijplatformplugincopy.intention.exceptions

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExceptPart
import javax.swing.Icon

class WrapExceptionsWithParenthesesIntention : PsiElementBaseIntentionAction(), PriorityAction, Iconable {
    override fun getFamilyName(): String = "Wrap exceptions with parentheses"

    override fun getText(): String = "Wrap exceptions with parentheses"

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun getIcon(flags: Int): Icon = AllIcons.Actions.QuickfixBulb

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        if (!PluginSettingsState.instance().state.enableWrapExceptionsWithParenthesesIntention) return false
        val exceptPart = PsiTreeUtil.getParentOfType(element, PyExceptPart::class.java) ?: return false
        // Ensure we have both a class and a target, separated by a comma, but NO 'as' keyword.
        val target = exceptPart.target
        if (exceptPart.exceptClass != null && target != null) {
            val comma = exceptPart.node.findChildByType(PyTokenTypes.COMMA)
            val asKeyword = exceptPart.node.findChildByType(PyTokenTypes.AS_KEYWORD)
            if (comma != null && asKeyword == null) {
                return true
            }
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val exceptPart = PsiTreeUtil.getParentOfType(element, PyExceptPart::class.java) ?: return
        val exceptClass = exceptPart.exceptClass ?: return
        val target = exceptPart.target ?: return
        val comma = exceptPart.node.findChildByType(PyTokenTypes.COMMA)?.psi ?: return

        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(element)

        val newExpressionText = "(${exceptClass.text}, ${target.text})"
        val newExpression = generator.createExpressionFromText(languageLevel, newExpressionText)

        exceptPart.exceptClass?.replace(newExpression)
        exceptPart.deleteChildRange(comma, target)
    }
}
