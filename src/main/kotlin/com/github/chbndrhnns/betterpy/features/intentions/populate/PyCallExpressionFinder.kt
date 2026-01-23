package com.github.chbndrhnns.betterpy.features.intentions.populate

import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFile

/**
 * Utility for finding PyCallExpression at the current caret position.
 */
class PyCallExpressionFinder {

    /**
     * Finds the PyCallExpression at the current caret position.
     * Returns null if the caret is not within a call expression's argument list.
     */
    fun findCallExpression(editor: Editor, file: PyFile): PyCallExpression? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null
        val call =
            PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java, /* strict = */ false) ?: return null

        val argumentList = call.argumentList ?: return null
        val textRange = argumentList.textRange ?: return null

        if (offset > textRange.startOffset && offset < textRange.endOffset) {
            return call
        }
        return null
    }
}
