package com.github.chbndrhnns.intellijplatformplugincopy.intention.dictAccess

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.util.isOwnCode
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDictAccessIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Toggle dictionary access"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        if (!element.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableDictAccessIntention) return false
        if (!PythonVersionGuard.isSatisfiedForElement(element)) return false
        // 1. Check Bracket Access: d[k]
        val subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java)
        if (subscription != null && subscription.indexExpression != null) {
            // Prevent replacing assignment targets: d[k] = v, del d[k]
            if (isAssignmentTarget(subscription)) return false
            if (PsiTreeUtil.getParentOfType(subscription, PyDelStatement::class.java) != null) return false

            val context = TypeEvalContext.codeAnalysis(project, element.containingFile)
            val type = context.getType(subscription.operand)
            if (type != null && PyABCUtil.isSubtype(type, PyNames.MAPPING, context)) {
                text = PluginConstants.ACTION_PREFIX + "Replace 'dict[key]' with 'dict.get(key)'"
                return true
            }
        }

        // 2. Check Get Access: d.get(k)
        val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java)
        if (call != null) {
            val callee = call.callee as? PyReferenceExpression
            if (callee != null && "get" == callee.name) {
                // Must have exactly 1 argument (key), no default value
                if (call.arguments.size == 1) {
                    val qualifier = callee.qualifier
                    if (qualifier != null) {
                        val context = TypeEvalContext.codeAnalysis(project, element.containingFile)
                        val type = context.getType(qualifier)
                        if (type != null && PyABCUtil.isSubtype(type, PyNames.MAPPING, context)) {
                            text = PluginConstants.ACTION_PREFIX + "Replace 'dict.get(key)' with 'dict[key]'"
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun isAssignmentTarget(element: PsiElement): Boolean {
        var current = element
        var parent = current.parent
        // Walk up through containers (tuples/lists) to find if we are part of an assignment structure
        while (parent is PyParenthesizedExpression || parent is PyTupleExpression || parent is PyListLiteralExpression) {
            current = parent
            parent = current.parent
        }
        if (parent is PyAssignmentStatement) {
            return parent.targets.any { it == current || it == element }
        }
        if (parent is PyAugAssignmentStatement) {
            return parent.target == current
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val generator = PyElementGenerator.getInstance(project)

        // Case 1: Bracket -> Get
        val subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java)
        if (subscription != null) {
            val operand = subscription.operand
            val indexExpr = subscription.indexExpression ?: return

            // Extract content between brackets to preserve comments
            val lBracket = subscription.node.findChildByType(PyTokenTypes.LBRACKET)
            val rBracket = subscription.node.findChildByType(PyTokenTypes.RBRACKET)

            if (lBracket != null && rBracket != null) {
                var content = subscription.containingFile.text.substring(
                    lBracket.textRange.endOffset,
                    rBracket.textRange.startOffset
                )

                // If key is a naked tuple like d[a, b], we need d.get((a, b))
                // PyTupleExpression represents "a, b". If it lacks parens in source, wrap it.
                if (indexExpr is PyTupleExpression && indexExpr.node.findChildByType(PyTokenTypes.LPAR) == null) {
                    content = "($content)"
                }

                val newText = "${operand.text}.get($content)"
                val expression = generator.createExpressionFromText(LanguageLevel.forElement(element), newText)
                val newElement = subscription.replace(expression)
                CodeStyleManager.getInstance(project).reformat(newElement)
            }
            return
        }

        // Case 2: Get -> Bracket
        val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java)
        if (call != null) {
            val callee = call.callee as? PyReferenceExpression ?: return
            val qualifier = callee.qualifier ?: return
            val arg = call.arguments.firstOrNull() ?: return

            // Extract key text
            val content = if (arg is PyKeywordArgument) {
                // d.get(key=k) -> d[k]. We lose 'key=' but keep the value.
                arg.valueExpression?.text ?: return
            } else {
                // Positional: d.get( k ) -> try to capture surrounding whitespace/comments
                val lPar = call.argumentList?.node?.findChildByType(PyTokenTypes.LPAR)
                val rPar = call.argumentList?.node?.findChildByType(PyTokenTypes.RPAR)
                if (lPar != null && rPar != null) {
                    call.containingFile.text.substring(lPar.textRange.endOffset, rPar.textRange.startOffset)
                } else {
                    arg.text
                }
            }

            val newText = "${qualifier.text}[$content]"
            val expression = generator.createExpressionFromText(LanguageLevel.forElement(element), newText)
            val newElement = call.replace(expression)
            CodeStyleManager.getInstance(project).reformat(newElement)
        }
    }
}