package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.apply.WrapApplier
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.preview.WrapPreview
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.ui.JbPopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyImportService
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyWrapHeuristics
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.UnionCandidates
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Intention that wraps an expression causing a type mismatch with the expected type constructor.
 * Uses the Python type system APIs for robust type inference.
 */
class WrapWithExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware {
    // Minimal retained state: dynamic text shown by the IDE, updated in isAvailable().
    private var lastText: String = "Wrap with expected type"

    private sealed interface WrapPlan {
        val element: PyExpression
    }

    private data class Single(
        override val element: PyExpression,
        val ctorName: String,
        val ctorElement: PsiNamedElement?
    ) : WrapPlan

    private data class UnionChoice(
        override val element: PyExpression,
        val candidates: List<Pair<String, PsiNamedElement?>>
    ) : WrapPlan

    private companion object {
        val PLAN_KEY: Key<WrapPlan> = Key.create("wrap.with.expected.plan")
    }

    private val imports = PyImportService()
    private val applier = WrapApplier(imports)
    private val previewBuilder = WrapPreview()

    override fun getText(): String = lastText

    override fun getFamilyName(): String = "Type mismatch wrapper"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val plan = analyzeAtCaret(project, editor, file) ?: run {
            editor.putUserData(PLAN_KEY, null)
            lastText = "Wrap with expected type"
            return false
        }
        editor.putUserData(PLAN_KEY, plan)

        // Update dynamic text used by tests to locate the intention
        lastText = when (plan) {
            is UnionChoice -> "Wrap with expected union type…"
            is Single -> plan.ctorName.takeIf { it.isNotBlank() }?.let { "Wrap with $it()" }
                ?: "Wrap with expected type"
        }
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val plan = editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file) ?: return

        when (plan) {
            is UnionChoice -> {
                val candidates = plan.candidates
                val popupHost = WrapWithExpectedTypeIntentionHooks.popupHost ?: JbPopupHost()
                popupHost.showChooser(
                    editor = editor,
                    title = "Select expected type",
                    items = candidates,
                    render = { it.first },
                    onChosen = { chosen ->
                        applier.apply(project, file, plan.element, chosen.first, chosen.second)
                    }
                )
            }

            is Single -> {
                applier.apply(project, file, plan.element, plan.ctorName, plan.ctorElement)
            }
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val plan =
            editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file) ?: return IntentionPreviewInfo.EMPTY
        return when (plan) {
            is UnionChoice -> IntentionPreviewInfo.EMPTY
            is Single -> {
                previewBuilder.build(file, plan.element, plan.ctorName)
            }
        }
    }

    override fun startInWriteAction(): Boolean = true

    private fun analyzeAtCaret(project: Project, editor: Editor, file: PsiFile): WrapPlan? {
        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null
        val names = PyTypeIntentions.computeTypeNames(elementAtCaret, context)
        if (names.actual == null || names.expected == null || names.actual == names.expected) return null

        val ctor = PyTypeIntentions.expectedCtorName(elementAtCaret, context)
        val annElement = names.expectedCtorElement
        val unionCtors = annElement?.let { UnionCandidates.collect(it, elementAtCaret) } ?: emptyList()

        if (unionCtors.size >= 2) {
            val wrappedWithAny =
                unionCtors.any { PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, it.first, it.second) }
            if (wrappedWithAny) return null
            return UnionChoice(elementAtCaret, unionCtors)
        }

        if (!ctor.isNullOrBlank()) {
            val ctorElem = (names.expectedElement as? PsiNamedElement)
            if (PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, ctor, ctorElem)) return null
            return Single(elementAtCaret, ctor, ctorElem)
        }
        return null
    }


}