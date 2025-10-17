package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression

/**
 * Intention that wraps an expression causing a type mismatch with the expected type constructor.
 */
class WrapWithExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware {

    private var problematicElement: PyExpression? = null

    override fun getText(): String = "Wrap with str()"

    override fun getFamilyName(): String = "Type mismatch wrapper"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        problematicElement = null

        // Find type mismatch at caret and extract the problematic element
        val element = findProblematicElementAtCaret(editor, file)
        if (element != null) {
            problematicElement = element
            return true
        }

        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = problematicElement ?: return

        val generator = PyElementGenerator.getInstance(project)
        val wrappedExpression = generator.createExpressionFromText(
            com.jetbrains.python.psi.LanguageLevel.getLatest(),
            "str(${element.text})"
        )

        element.replace(wrappedExpression)
    }

    override fun startInWriteAction(): Boolean = true

    private fun findProblematicElementAtCaret(editor: Editor, file: PsiFile): PyExpression? {
        val offset = editor.caretModel.offset

        // First, try to find element at caret position
        val elementAtCaret = file.findElementAt(offset) ?: return null

        // Walk up the PSI tree to find a PyExpression, preferring call expressions
        var current: PsiElement? = elementAtCaret
        var bestExpression: PyExpression? = null

        while (current != null) {
            if (current is PyExpression) {
                bestExpression = current
                // If we find a call expression, prefer that over simple literals
                if (current is com.jetbrains.python.psi.PyCallExpression) {
                    return current
                }
            }
            current = current.parent
        }

        return bestExpression
    }

}