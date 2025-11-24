package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.CustomTypeApplier
import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.PlanBuilder
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyFile
import javax.swing.Icon

/**
 * Intention that introduces a simple custom domain type from a stdlib/builtin
 * type used in an annotation. For the first iteration we keep the behavior
 * deliberately small and test-driven:
 *
 * - Only reacts to builtin names like ``int`` when used in annotations.
 * - Generates a subclass ``CustomInt(int): pass`` in the current module.
 * - Rewrites the current annotation to refer to the new type.
 */
class IntroduceCustomTypeFromStdlibIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {

    private var lastText: String = "Introduce custom type from stdlib type"
    private val planBuilder = PlanBuilder()
    private val applier = CustomTypeApplier()

    override fun getText(): String = lastText

    override fun getFamilyName(): String = "Introduce custom type from stdlib type"

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableIntroduceCustomTypeFromStdlibIntention) {
            return false
        }

        val pyFile = file as? PyFile ?: return false

        val caretOffset = editor.caretModel.offset
        val highlights = DaemonCodeAnalyzerImpl.getHighlights(editor.document, null, project)
        if (highlights != null) {
            for (info in highlights) {
                if (info.description != null &&
                    info.startOffset <= caretOffset &&
                    info.endOffset >= caretOffset
                ) {
                    if ("PyTypeCheckerInspection" == info.inspectionToolId ||
                        "PyArgumentListInspection" == info.inspectionToolId
                    ) {
                        return false
                    }
                }
            }
        }

        val plan = planBuilder.build(editor, pyFile) ?: run {
            lastText = "Introduce custom type from stdlib type"
            return false
        }

        lastText = "Introduce custom type from ${plan.builtinName}"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val pyFile = file as? PyFile ?: return
        val plan = planBuilder.build(editor, pyFile) ?: return
        applier.apply(project, editor, plan)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        if (!PluginSettingsState.instance().state.enableIntroduceCustomTypeFromStdlibIntention) {
            return IntentionPreviewInfo.EMPTY
        }
        val pyFile = file as? PyFile ?: return IntentionPreviewInfo.EMPTY
        val plan = planBuilder.build(editor, pyFile) ?: return IntentionPreviewInfo.EMPTY
        applier.apply(project, editor, plan, isPreview = true)
        return IntentionPreviewInfo.DIFF
    }

    override fun startInWriteAction(): Boolean = true

}
