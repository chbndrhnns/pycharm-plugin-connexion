package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.CtorMatch
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon

class UnwrapItemsToExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {
    private var lastText: String = "Unwrap items"

    private companion object {
        val PLAN_KEY: Key<UnwrapItemsPlan> = Key.create("unwrap.items.plan")
    }

    private data class UnwrapItemsPlan(
        val container: PyExpression,
        val wrapperName: String
    )

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun getText(): String = lastText

    override fun getFamilyName(): String = "Type mismatch unwrapping (items)"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableUnwrapItemsToExpectedTypeIntention) {
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
        lastText = "Unwrap items ${plan.wrapperName}()"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val plan = editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            val elements = when (val c = plan.container) {
                is PySequenceExpression -> c.elements
                else -> emptyArray()
            }

            for (el in elements) {
                val info = PyTypeIntentions.getWrapperCallInfo(el) ?: continue
                if (info.name == plan.wrapperName) {
                    el.replace(info.inner)
                }
            }
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    private fun analyzeAtCaret(project: Project, editor: Editor, file: PsiFile): UnwrapItemsPlan? {
        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null

        if (elementAtCaret is PyTargetExpression) return null

        val containerItemTarget = PyTypeIntentions.findContainerItemAtCaret(editor, elementAtCaret) ?: elementAtCaret
        val parent = containerItemTarget.parent
        val isLiteral = parent is PyListLiteralExpression ||
                parent is PySetLiteralExpression ||
                parent is PyTupleExpression

        if (!isLiteral) return null
        val containerExpr = parent as PyExpression

        // 1. Identify wrapper
        val wrapperInfo = PyTypeIntentions.getWrapperCallInfo(containerItemTarget) ?: return null
        val wrapperName = wrapperInfo.name
        val innerExpr = wrapperInfo.inner

        // 2. Expected item type of the container
        val expectedItemCtor = PyTypeIntentions.tryContainerItemCtor(containerItemTarget, context) ?: return null

        // 3. Verify inner expression matches expected item type
        val match = PyTypeIntentions.elementDisplaysAsCtor(innerExpr, expectedItemCtor.name, context)
        if (match != CtorMatch.MATCHES) return null

        return UnwrapItemsPlan(containerExpr, wrapperName)
    }
}
