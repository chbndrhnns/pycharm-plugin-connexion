package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.JbPopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.util.isOwnCode
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon

/**
 * Hooks for testing the PopulateArgumentsIntention.
 * Allows injecting a fake PopupHost for unit tests.
 */
object PopulateArgumentsIntentionHooks {
    var popupHost: PopupHost? = null
}

/**
 * Unified intention that populates missing call arguments with placeholder values.
 * Shows a popup chooser allowing the user to select:
 * - All arguments vs. Required only
 * - Recursive expansion for nested dataclasses/Pydantic models
 *
 * Example:
 *     A(<caret>) -> A(x=..., y=...)
 *     A(<caret>) -> A(b=B(c=...))  (recursive mode)
 */
class PopulateArgumentsIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {

    private val service = PopulateArgumentsService()

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Populate arguments..."

    override fun getFamilyName(): String = "Populate arguments"

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enablePopulateArgumentsIntention) return false
        if (file !is PyFile) return false

        val call = service.findCallExpression(editor, file) ?: return false
        val ctx = TypeEvalContext.codeAnalysis(project, file)

        return service.isAvailable(call, ctx)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (!PluginSettingsState.instance().state.enablePopulateArgumentsIntention) return
        val pyFile = file as? PyFile ?: return
        val call = service.findCallExpression(editor, pyFile) ?: return
        val ctx = TypeEvalContext.userInitiated(project, pyFile)

        if (!service.isAvailable(call, ctx)) return

        // Determine available options based on whether recursive is applicable
        val options = if (service.isRecursiveApplicable(call, ctx)) {
            PopulateOptions.ALL_OPTIONS
        } else {
            PopulateOptions.NON_RECURSIVE_OPTIONS
        }

        val popupHost = PopulateArgumentsIntentionHooks.popupHost ?: JbPopupHost()
        popupHost.showChooser(
            editor = editor,
            title = "Populate arguments",
            items = options,
            render = { it.label() },
            onChosen = { chosen ->
                // The chooser callback runs outside the original intention command.
                // Wrap PSI modifications into a write command to satisfy the platform contract.
                WriteCommandAction.runWriteCommandAction(project, text, null, Runnable {
                    service.populateArguments(project, pyFile, call, chosen, ctx)
                }, pyFile)
            }
        )
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    override fun startInWriteAction(): Boolean = false
}
