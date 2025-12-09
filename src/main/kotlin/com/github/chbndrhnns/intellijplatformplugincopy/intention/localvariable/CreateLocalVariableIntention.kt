package com.github.chbndrhnns.intellijplatformplugincopy.intention.localvariable

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.codeInsight.intentions.PyBaseIntentionAction
import com.jetbrains.python.psi.*

class CreateLocalVariableIntention : PyBaseIntentionAction() {

    override fun getFamilyName(): String {
        return "Create local variable"
    }

    override fun getText(): String {
        return "Create local variable"
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableCreateLocalVariableIntention) return false
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val refExpr = PsiTreeUtil.getParentOfType(element, PyReferenceExpression::class.java) ?: return false
        if (refExpr.isQualified) return false
        return refExpr.reference.resolve() == null
    }

    @Throws(IncorrectOperationException::class)
    override fun doInvoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val refExpr = PsiTreeUtil.getParentOfType(element, PyReferenceExpression::class.java) ?: return

        val name = refExpr.text
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(file)

        val assignment = generator.createFromText(
            languageLevel,
            PyAssignmentStatement::class.java,
            "$name = None"
        )

        val currentStatement = PsiTreeUtil.getParentOfType(refExpr, PyStatement::class.java)
        currentStatement?.parent?.addBefore(assignment, currentStatement)
    }
}
