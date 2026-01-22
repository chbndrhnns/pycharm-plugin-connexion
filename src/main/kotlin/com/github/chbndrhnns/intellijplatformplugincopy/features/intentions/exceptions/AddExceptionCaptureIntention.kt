package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.exceptions

import com.github.chbndrhnns.intellijplatformplugincopy.core.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.core.util.isOwnCode
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyExceptPart
import com.jetbrains.python.psi.PyRaiseStatement
import com.jetbrains.python.psi.PyReferenceExpression

class AddExceptionCaptureIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Add exception capture"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        if (!element.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableAddExceptionCaptureIntention) return false
        val raiseStatement = PsiTreeUtil.getParentOfType(element, PyRaiseStatement::class.java) ?: return false
        val fromKeyword = raiseStatement.node.findChildByType(PyTokenTypes.FROM_KEYWORD) ?: return false
        val fromExpression = raiseStatement.expressions.firstOrNull { it.textRange.startOffset > fromKeyword.startOffset } ?: return false
        
        if (fromExpression !is PyReferenceExpression || fromExpression.isQualified) return false
        val fromName = fromExpression.text

        val exceptPart = PsiTreeUtil.getParentOfType(raiseStatement, PyExceptPart::class.java) ?: return false
        if (exceptPart.target != null) return false

        // Ensure there is an exception class specified (bare except: cannot have as target?)
        // In Python, `except:` matches all. `except as x:` is invalid.
        // `except Exception:` is required for `as`.
        if (exceptPart.exceptClass == null) return false

        text = PluginConstants.ACTION_PREFIX + "Add 'as $fromName' to except clause"
        return true
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val raiseStatement = PsiTreeUtil.getParentOfType(element, PyRaiseStatement::class.java) ?: return
        val fromKeyword = raiseStatement.node.findChildByType(PyTokenTypes.FROM_KEYWORD) ?: return
        val fromExpression = raiseStatement.expressions.firstOrNull { it.textRange.startOffset > fromKeyword.startOffset } ?: return
        val fromName = fromExpression.text

        val exceptPart = PsiTreeUtil.getParentOfType(raiseStatement, PyExceptPart::class.java) ?: return
        if (exceptPart.target != null) return
        exceptPart.exceptClass ?: return

        val colon = exceptPart.node.findChildByType(PyTokenTypes.COLON) ?: return
        val offset = colon.startOffset

        editor.document.insertString(offset, " as $fromName")
    }
}
