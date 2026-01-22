package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions

import com.github.chbndrhnns.intellijplatformplugincopy.core.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.core.util.isOwnCode
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.shared.CtorMatch
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.shared.WrapperInfo
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
import com.jetbrains.python.psi.PyStarArgument
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

    private var lastText: String = PluginConstants.ACTION_PREFIX + "Unwrap to expected type"

    private data class UnwrapContext(
        val call: PyCallExpression,
        val inner: PyExpression,
        val wrapperName: String,
    )

    private companion object {
        val PLAN_KEY: Key<UnwrapContext> = Key.create("unwrap.to.expected.plan")
    }

    override fun getText(): String = lastText

    override fun getFamilyName(): String = "Type mismatch unwrapping"

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableUnwrapToExpectedTypeIntention) {
            editor.putUserData(PLAN_KEY, null)
            return false
        }

        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file)
        if (elementAtCaret?.parent is PyStarArgument) {
            editor.putUserData(PLAN_KEY, null)
            return false
        }

        val plan = analyzeAtCaret(project, editor, file) ?: run {
            editor.putUserData(PLAN_KEY, null)
            return false
        }

        editor.putUserData(PLAN_KEY, plan)
        lastText = PluginConstants.ACTION_PREFIX + "Unwrap ${plan.wrapperName}()"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val plan = editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file) ?: return
        applyUnwrap(plan)
    }

    override fun startInWriteAction(): Boolean = true

    private fun analyzeAtCaret(project: Project, editor: Editor, file: PsiFile): UnwrapContext? {
        val context = TypeEvalContext.codeAnalysis(project, file)
        val original = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null

        val wrapperInfo = resolveTargetWrapper(editor, original) ?: return null

        val call = wrapperInfo.call
        val innerExpr = wrapperInfo.inner
        val wrapperName = wrapperInfo.name

        // Only non-container wrappers.
        if (wrapperName.lowercase() in PyTypeIntentions.CONTAINERS) return null

        // Expected type at this usage site.
        // First check if we are an item in a container (list/set/tuple literal)
        val containerItemCtor = PyTypeIntentions.tryContainerItemCtor(call, context)
        val expected = if (containerItemCtor != null) {
            containerItemCtor.name
        } else {
            val outerNames = PyTypeIntentions.computeDisplayTypeNames(call, context)
            if (outerNames.expected == "Unknown") null else outerNames.expected
        }

        // Only unwrap when the inner expression already satisfies the expected type.
        // Unlike the wrapping intention, we do NOT require the wrapper call's own
        // displayed type to differ from the expected type, because in some environments
        // (notably NewType) the call expression may already be reported as the
        // underlying type even though it is conceptually a value-object wrapper.
        //
        // If the expected type is unknown (e.g. no annotation on return/assignment),
        // we check if the inner expression matches the wrapper's own type.
        // This covers cases like `return int(val)` where `val` is already `int`.
        val targetType = expected ?: wrapperName
        val match = PyTypeIntentions.elementDisplaysAsCtor(innerExpr, targetType, context)
        if (match != CtorMatch.MATCHES) return null

        return UnwrapContext(call, innerExpr, wrapperName)
    }

    private fun resolveTargetWrapper(editor: Editor, original: PyExpression): WrapperInfo? {
        // Support caret both on the call itself and on its single argument, including when the
        // caret sits on the literal or on parentheses inside the argument. This mirrors how
        // WrapWithExpectedTypeIntention first finds the expression at caret and then may move
        // to its parent when deciding what to transform.
        val flattened = PyPsiUtils.flattenParens(original) ?: original

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

        return when (caretTarget) {
            is PyCallExpression -> PyTypeIntentions.getWrapperCallInfo(caretTarget)
            else -> {
                // When the caret is on the inner literal or parentheses, the parent of the
                // expression-at-caret is often a PyArgumentList rather than the call itself.
                // Walk up to the nearest enclosing PyCallExpression and ensure the caret
                // expression is (or belongs to) its single argument.
                val parentCall = PsiTreeUtil.getParentOfType(caretTarget, PyCallExpression::class.java)
                val info = if (parentCall != null) PyTypeIntentions.getWrapperCallInfo(parentCall) else null

                if (info != null) {
                    val soleArg = info.inner
                    // Only unwrap when the caret is on that single argument or inside it.
                    if (soleArg != caretTarget && !PsiTreeUtil.isAncestor(soleArg, caretTarget, false)) {
                        null
                    } else {
                        info
                    }
                } else {
                    null
                }
            }
        }
    }

    private fun applyUnwrap(plan: UnwrapContext) {
        val inner = PyPsiUtils.flattenParens(plan.inner) ?: plan.inner
        plan.call.replace(inner)
    }
}
