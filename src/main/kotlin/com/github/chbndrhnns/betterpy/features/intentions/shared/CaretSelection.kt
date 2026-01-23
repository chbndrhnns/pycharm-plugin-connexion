package com.github.chbndrhnns.betterpy.features.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

/** Caret scanning and selection utilities. */
internal object CaretSelection {

    private data class CaretCandidates(
        val string: PyExpression? = null,
        val call: PyExpression? = null,
        val parenthesized: PyExpression? = null,
        val other: PyExpression? = null,
    )

    private fun collectCandidates(leaf: PsiElement, file: PsiFile): CaretCandidates {
        var current: PsiElement? = leaf
        var bestString: PyExpression? = null
        var bestCall: PyExpression? = null
        var bestParenthesized: PyExpression? = null
        var bestOther: PyExpression? = null

        while (current != null && current != file) {
            if (current is PyExpression) {
                when (current) {
                    is PyCallExpression -> bestCall = current
                    is PyStringLiteralExpression -> bestString = current
                    is PyParenthesizedExpression -> bestParenthesized = current
                    else -> if (bestOther == null) bestOther = current
                }
            }
            current = current.parent
        }
        return CaretCandidates(bestString, bestCall, bestParenthesized, bestOther)
    }

    private fun chooseBest(c: CaretCandidates, leaf: PsiElement): PyExpression? {
        if (c.string != null && isInsideFunctionCallArgument(c.string)) return c.string
        if (c.parenthesized != null && isInsideFunctionCallArgument(c.parenthesized))
            return c.string ?: c.parenthesized

        val inArg = listOfNotNull(c.call, c.other).any { isInsideFunctionCallArgument(it) }
        if (inArg) {
            val argList = PsiTreeUtil.getParentOfType(leaf, PyArgumentList::class.java)
            val call = PsiTreeUtil.getParentOfType(leaf, PyCallExpression::class.java)
            if (argList != null && call != null) {
                val args = argList.arguments
                val arg = args.firstOrNull { it == leaf || PsiTreeUtil.isAncestor(it, leaf, false) }
                if (arg is PyKeywordArgument) {
                    arg.valueExpression?.let { return it }
                } else if (arg is PyExpression) {
                    return arg
                }
            }
            return c.call ?: c.other
        }
        return c.call ?: c.parenthesized ?: c.string ?: c.other
    }

    fun findExpressionAtCaret(editor: Editor, file: PsiFile): PyExpression? {
        val offset = editor.caretModel.offset
        val leaf = file.findElementAt(offset) ?: return null

        PsiTreeUtil.getParentOfType(leaf, PyKeywordArgument::class.java)?.let { kw ->
            val value = kw.valueExpression
            if (value != null && !PsiTreeUtil.isAncestor(value, leaf, false)) {
                return value
            }
        }

        argumentRootAtCaret(leaf)?.let { candidate ->
            if (isInsideFunctionCallArgument(candidate)) return candidate
        }

        val candidates = collectCandidates(leaf, file)
        return chooseBest(candidates, leaf)
    }

    fun findContainerItemAtCaret(editor: Editor, containerOrElement: PyExpression): PyExpression? {
        val offset = editor.caretModel.offset
        return when (containerOrElement) {
            is PyListLiteralExpression -> {
                val elems = containerOrElement.elements
                val exact = elems.firstOrNull { it.textRange.containsOffset(offset) }
                exact ?: run {
                    val right = elems.firstOrNull { it.textRange.startOffset >= offset }
                    val left = elems.lastOrNull { it.textRange.endOffset <= offset }
                    right ?: left
                }
            }

            is PySetLiteralExpression -> {
                val elems = containerOrElement.elements
                val exact = elems.firstOrNull { it.textRange.containsOffset(offset) }
                exact ?: run {
                    val right = elems.firstOrNull { it.textRange.startOffset >= offset }
                    val left = elems.lastOrNull { it.textRange.endOffset <= offset }
                    right ?: left
                }
            }

            is PyTupleExpression -> {
                val elems = containerOrElement.elements
                val exact = elems.firstOrNull { it.textRange.containsOffset(offset) }
                exact ?: run {
                    val right = elems.firstOrNull { it.textRange.startOffset >= offset }
                    val left = elems.lastOrNull { it.textRange.endOffset <= offset }
                    right ?: left
                }
            }

            is PyDictLiteralExpression -> {
                val pairs = containerOrElement.elements
                val exactInPair = pairs.firstOrNull { pair ->
                    val k = pair.key
                    val v = pair.value
                    (k.textRange.containsOffset(offset)) || (v != null && v.textRange.containsOffset(offset))
                }
                when {
                    exactInPair != null -> {
                        val k = exactInPair.key
                        val v = exactInPair.value
                        when {
                            k.textRange.containsOffset(offset) -> k
                            v != null && v.textRange.containsOffset(offset) -> v
                            else -> null
                        }
                    }

                    else -> {
                        val right = pairs.firstOrNull { it.textRange.startOffset >= offset }
                        val left = pairs.lastOrNull { it.textRange.endOffset <= offset }
                        val pair = right ?: left
                        (pair?.key ?: pair?.value)
                    }
                }
            }

            else -> null
        }
    }

    private fun argumentRootAtCaret(leaf: PsiElement): PyExpression? {
        val argList = PsiTreeUtil.getParentOfType(leaf, PyArgumentList::class.java) ?: return null
        val args = argList.arguments
        val arg = args.firstOrNull { it == leaf || PsiTreeUtil.isAncestor(it, leaf, false) }
        return when (arg) {
            is PyKeywordArgument -> arg.valueExpression
            is PyStarArgument -> PsiTreeUtil.getChildOfType(arg, PyExpression::class.java)
            is PyExpression -> arg
            else -> null
        }
    }

    private fun inArgList(expr: PyExpression): Pair<PyArgumentList, PyCallExpression>? {
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java) ?: return null
        val call = PsiTreeUtil.getParentOfType(argList, PyCallExpression::class.java) ?: return null

        val inPositional = argList.arguments.any { it == expr || PsiTreeUtil.isAncestor(it, expr, false) }

        val inKeyword = argList.arguments.asSequence()
            .mapNotNull { it as? PyKeywordArgument }
            .any { kw ->
                val v = kw.valueExpression
                v != null && (v == expr || PsiTreeUtil.isAncestor(v, expr, false))
            }

        return if (inPositional || inKeyword) argList to call else null
    }

    private fun isExcludedAssignment(call: PyCallExpression): Boolean {
        val assignment = PsiTreeUtil.getParentOfType(call, PyAssignmentStatement::class.java)
        if (assignment != null && assignment.assignedValue == call) {
            val hasAnnotation = assignment.targets.any { target ->
                (target as? PyTargetExpression)?.annotation != null
            }
            if (hasAnnotation) return true
        }
        return false
    }

    private fun isExcludedReturn(call: PyCallExpression): Boolean {
        val returnStmt = PsiTreeUtil.getParentOfType(call, PyReturnStatement::class.java)
        return returnStmt != null && returnStmt.expression == call
    }

    private fun isInsideFunctionCallArgument(expr: PyExpression): Boolean {
        val pair = inArgList(expr) ?: return false
        val call = pair.second
        if (isExcludedAssignment(call)) return false
        if (isExcludedReturn(call)) return false
        return true
    }
}
