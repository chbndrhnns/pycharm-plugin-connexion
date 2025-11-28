package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.CustomTypeApplier
import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.CustomTypePlan
import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.PlanBuilder
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStarArgument
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

        val vFile = file.virtualFile ?: return false
        if (!ProjectFileIndex.getInstance(project).isInContent(vFile)) {
            return false
        }

        val pyFile = file as? PyFile ?: return false

        val elementAtCaret =
            PyTypeIntentions.findExpressionAtCaret(
                editor,
                file
            )
        if (elementAtCaret?.parent is PyStarArgument) {
            editor.putUserData(PLAN_KEY, null)
            return false
        }

        // Build and cache the plan first so we know the logical PSI target
        val plan = planBuilder.build(editor, pyFile)
        editor.putUserData(PLAN_KEY, plan)

        if (plan == null) {
            lastText = "Introduce custom type from stdlib type"
            return false
        }

        // Block when there are type checker / argument list errors anywhere in the
        // logical construct we are about to operate on (annotation, parameter,
        // assignment, dataclass field, etc.), not just exactly at the caret.
        val targetRange = plan.targetElement?.textRange
        if (targetRange != null && hasBlockingInspections(project, editor, targetRange)) {
            return false
        }

        lastText = "Introduce custom type from ${plan.builtinName}"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val plan = getPlan(editor, file) ?: return
        applier.apply(project, editor, plan)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        if (!PluginSettingsState.instance().state.enableIntroduceCustomTypeFromStdlibIntention) {
            return IntentionPreviewInfo.EMPTY
        }
        val plan = getPlan(editor, file) ?: return IntentionPreviewInfo.EMPTY
        applier.apply(project, editor, plan, isPreview = true)
        return IntentionPreviewInfo.DIFF
    }

    override fun startInWriteAction(): Boolean = true

    private fun getPlan(editor: Editor, file: PsiFile): CustomTypePlan? {
        return editor.getUserData(PLAN_KEY) ?: run {
            // Fallback if invoke is called without isAvailable (rare but possible in tests/scripts)
            val pyFile = file as? PyFile ?: return null
            planBuilder.build(editor, pyFile)
        }
    }

    private fun hasBlockingInspections(project: Project, editor: Editor, range: TextRange): Boolean {
        val highlights = DaemonCodeAnalyzerImpl.getHighlights(editor.document, null, project)

        return highlights.any { info ->
            info.description != null &&
                    (info.inspectionToolId == "PyTypeCheckerInspection" ||
                            info.inspectionToolId == "PyArgumentListInspection") &&
                    TextRange(info.startOffset, info.endOffset).intersects(range)
        }
    }

    companion object {
        private val PLAN_KEY = Key.create<CustomTypePlan>("introduce.custom.type.plan")

    }
}
