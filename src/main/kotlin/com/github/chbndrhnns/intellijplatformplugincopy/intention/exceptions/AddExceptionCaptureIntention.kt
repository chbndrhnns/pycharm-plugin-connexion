package com.github.chbndrhnns.intellijplatformplugincopy.intention.exceptions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*

class AddExceptionCaptureIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Add exception capture"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
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

        text = "Add 'as $fromName' to except clause"
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

        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(element)
        
        // Create a dummy except part to extract 'as target' elements
        val dummyFile = generator.createDummyFile(languageLevel, "try: pass\nexcept Exception as $fromName: pass")
        val dummyExcept = PsiTreeUtil.findChildOfType(dummyFile, PyExceptPart::class.java) ?: return
        
        val asToken = dummyExcept.node.findChildByType(PyTokenTypes.AS_KEYWORD)?.psi
        val target = dummyExcept.target

        if (asToken != null && target != null) {
            val parserFacade = com.intellij.psi.PsiParserFacade.getInstance(project)
            val space = parserFacade.createWhiteSpaceFromText(" ")
            
            // Insert before colon
            val colon = exceptPart.node.findChildByType(PyTokenTypes.COLON)?.psi
            
            if (colon != null) {
                 val addedTarget = exceptPart.addBefore(target, colon)
                 val addedSpace2 = exceptPart.addBefore(space, addedTarget)
                 val addedAs = exceptPart.addBefore(asToken, addedSpace2)
                 exceptPart.addBefore(space, addedAs)
            }
        }
    }
}
