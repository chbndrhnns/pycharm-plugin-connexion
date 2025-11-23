package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon

/**
 * Intention that unwraps a non-container wrapper call when the inner expression already
 * satisfies the expected type at the current position (assignment, argument, return, etc.).
 *
 * Mirrors [WrapWithExpectedTypeIntention] but in the opposite direction.
 */
class UnwrapToExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {

    private var lastText: String = "Unwrap to expected type"

    private sealed interface UnwrapPlan {
        val call: PyCallExpression
        val inner: PyExpression
        val wrapperName: String
    }

    private data class SingleUnwrap(
        override val call: PyCallExpression,
        override val inner: PyExpression,
        override val wrapperName: String,
    ) : UnwrapPlan

    private companion object {
        val PLAN_KEY: Key<UnwrapPlan> = Key.create("unwrap.to.expected.plan")
        private val CONTAINERS = setOf("list", "set", "tuple", "dict")
    }

    override fun getText(): String = lastText

    override fun getFamilyName(): String = "Type mismatch unwrapping"

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableUnwrapIntention) {
            editor.putUserData(PLAN_KEY, null)
            lastText = "Unwrap to expected type"
            return false
        }

        val plan = analyzeAtCaret(project, editor, file) ?: run {
            editor.putUserData(PLAN_KEY, null)
            lastText = "Unwrap to expected type"
            return false
        }

        editor.putUserData(PLAN_KEY, plan)
        lastText = when (plan) {
            is SingleUnwrap -> "Unwrap ${plan.wrapperName}()"
        }
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val plan = editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file) ?: return
        when (plan) {
            is SingleUnwrap -> applyUnwrap(project, plan)
        }
    }

    override fun startInWriteAction(): Boolean = true

    private fun analyzeAtCaret(project: Project, editor: Editor, file: PsiFile): UnwrapPlan? {
        val context = TypeEvalContext.codeAnalysis(project, file)
        val original = PyTypeIntentions.findExpressionAtCaret(editor, file) as? PyExpression ?: return null

        // Support caret both on the call itself and on its single argument, including when the
        // caret sits on the literal or on parentheses inside the argument. This mirrors how
        // WrapWithExpectedTypeIntention first finds the expression at caret and then may move
        // to its parent when deciding what to transform.
        val flattened = PyPsiUtils.flattenParens(original) as? PyExpression ?: original

        // If the caret is actually inside the sole argument of a call expression, prefer to
        // treat that argument as the caret expression. This allows us to unwrap the inner
        // argument call (e.g. `UserId(10)`) even when the outer call (e.g. `f(...)`) is the
        // expression that findExpressionAtCaret returned.
        val offset = editor.caretModel.offset
        val caretTarget: PyExpression = when (flattened) {
            is PyCallExpression -> {
                val args = flattened.arguments
                if (args.size == 1) {
                    val soleArg = args[0]
                    if (soleArg is PyExpression && soleArg.textRange.containsOffset(offset)) {
                        soleArg
                    } else {
                        flattened
                    }
                } else {
                    flattened
                }
            }

            else -> flattened
        }
        val call: PyCallExpression
        val innerExpr: PyExpression

        when (caretTarget) {
            is PyCallExpression -> {
                call = caretTarget
                val args = call.arguments
                if (args.size != 1) return null
                innerExpr = args[0] as? PyExpression ?: return null
            }

            else -> {
                // When the caret is on the inner literal or parentheses, the parent of the
                // expression-at-caret is often a PyArgumentList rather than the call itself.
                // Walk up to the nearest enclosing PyCallExpression and ensure the caret
                // expression is (or belongs to) its single argument.
                val parentCall = PsiTreeUtil.getParentOfType(caretTarget, PyCallExpression::class.java)
                    ?: return null
                val args = parentCall.arguments
                if (args.size != 1) return null

                val soleArg = args[0]
                // Only unwrap when the caret is on that single argument or inside it.
                if (soleArg != caretTarget && !PsiTreeUtil.isAncestor(soleArg, caretTarget, false)) return null

                call = parentCall
                innerExpr = soleArg as? PyExpression ?: return null
            }
        }

        val calleeRef = call.callee as? PyReferenceExpression ?: return null
        val wrapperName = calleeRef.name ?: return null

        // Only non-container wrappers.
        if (wrapperName.lowercase() in CONTAINERS) return null

        // Expected type at this usage site.
        // First check if we are an item in a container (list/set/tuple literal)
        val containerItemCtor = PyTypeIntentions.tryContainerItemCtor(call, context)
        val expected = if (containerItemCtor != null) {
            containerItemCtor.name
        } else {
            val outerNames = PyTypeIntentions.computeDisplayTypeNames(call, context)
            outerNames.expected
        } ?: return null

        // Type of the inner expression when used here.
        val innerNames = PyTypeIntentions.computeDisplayTypeNames(innerExpr, context)
        val innerActual = innerNames.actual ?: return null

        // Only unwrap when the inner expression already satisfies the expected type.
        // Unlike the wrapping intention, we do NOT require the wrapper call's own
        // displayed type to differ from the expected type, because in some environments
        // (notably NewType) the call expression may already be reported as the
        // underlying type even though it is conceptually a value-object wrapper.
        if (innerActual != expected) return null

        return SingleUnwrap(call, innerExpr, wrapperName)
    }

    private fun applyUnwrap(project: Project, plan: SingleUnwrap) {
        val inner = PyPsiUtils.flattenParens(plan.inner) as? PyExpression ?: plan.inner
        plan.call.replace(inner)
    }
}
