package com.github.chbndrhnns.intellijplatformplugincopy.features.navigation

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Provides "Go to Declaration" support for the attribute string in `patch.object(target, "attribute")`.
 * When the caret is on the string literal, navigates to the actual attribute on the target class.
 */
class PyMockPatchObjectGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        if (!PluginSettingsState.instance().state.enablePyMockPatchReferenceContributor) return null

        // Find the string literal containing the caret
        val stringLiteral = sourceElement.parent as? PyStringLiteralExpression ?: return null

        // Find the call expression
        val argumentList = stringLiteral.parent ?: return null
        val callExpression = argumentList.parent as? PyCallExpression ?: return null

        if (!isPatchObjectCall(callExpression)) return null

        val arguments = callExpression.argumentList?.arguments ?: return null
        if (arguments.size < 2) return null

        // Verify this string is the second argument (attribute name)
        if (arguments[1] != stringLiteral) return null

        val targetArg = arguments[0]
        val attributeName = stringLiteral.stringValue

        val context = TypeEvalContext.codeAnalysis(callExpression.project, callExpression.containingFile)
        val targetType = context.getType(targetArg) ?: return null

        val members = targetType.resolveMember(
            attributeName,
            null,
            AccessDirection.READ,
            PyResolveContext.defaultContext(context)
        )

        if (members.isNullOrEmpty()) return null

        return members.mapNotNull { it.element }.toTypedArray()
    }

    private fun isPatchObjectCall(call: PyCallExpression): Boolean {
        val callee = call.callee as? PyReferenceExpression ?: return false

        if (callee.name == "object") {
            val qualifier = callee.qualifier as? PyReferenceExpression
            if (qualifier?.name == "patch") {
                return true
            }
        }
        return false
    }
}
