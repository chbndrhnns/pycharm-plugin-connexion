package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.CtorMatch
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
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
        val candidates: List<ExpectedCtor>
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
                    render = { it.name },
                    onChosen = { chosen ->
                        applier.apply(project, file, plan.element, chosen.name, chosen.symbol)
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

        // Prefer container-element analysis (e.g., items inside list literals) before generic logic.
        // When the caret expression is a container literal, locate the specific item under the caret
        // and target that element for wrapping; otherwise keep the original element.
        val containerItemTarget = PyTypeIntentions.findContainerItemAtCaret(editor, elementAtCaret)
            ?: elementAtCaret

        // Always attempt element-level container analysis: the helper discovers the enclosing container itself
        PyTypeIntentions.tryContainerItemCtor(containerItemTarget, context)?.let { ctor ->
            val suppressedContainers = setOf("list", "set", "tuple", "dict")
            if (!suppressedContainers.contains(ctor.name.lowercase())) {
                // Suppress if the element already has the expected type (e.g., int in list[int])
                if (PyTypeIntentions.elementDisplaysAsCtor(
                        containerItemTarget,
                        ctor.name,
                        context
                    ) == CtorMatch.MATCHES
                ) {
                    return null
                }
                if (!PyWrapHeuristics.isAlreadyWrappedWith(containerItemTarget, ctor.name, ctor.symbol)) {
                    return Single(containerItemTarget, ctor.name, ctor.symbol)
                }
            }
        }
        // Suppress generic wrapping when caret is on container literals; we only support element-level here
        if (elementAtCaret is com.jetbrains.python.psi.PyListLiteralExpression ||
            elementAtCaret is com.jetbrains.python.psi.PySetLiteralExpression ||
            elementAtCaret is com.jetbrains.python.psi.PyTupleExpression ||
            elementAtCaret is com.jetbrains.python.psi.PyDictLiteralExpression
        ) return null

        val names = PyTypeIntentions.computeTypeNames(elementAtCaret, context)
        if (names.actual == null || names.expected == null || names.actual == names.expected) return null

        val ctor = PyTypeIntentions.expectedCtorName(elementAtCaret, context)
        val annElement = names.expectedCtorElement
        val unionCtors = annElement?.let { UnionCandidates.collect(it, elementAtCaret) } ?: emptyList()

        if (unionCtors.size >= 2) {
            // Filter out candidates that already match the element's actual displayed type
            val differing = unionCtors.filter {
                PyTypeIntentions.elementDisplaysAsCtor(elementAtCaret, it.name, context) == CtorMatch.DIFFERS
            }
            val wrappedWithAny = differing.any {
                PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, it.name, it.symbol)
            }
            if (wrappedWithAny) return null

            when (differing.size) {
                0 -> return null
                1 -> {
                    val only = differing.first()
                    if (!PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, only.name, only.symbol))
                        return Single(elementAtCaret, only.name, only.symbol)
                    return null
                }

                else -> return UnionChoice(elementAtCaret, differing)
            }
        }

        if (!ctor.isNullOrBlank()) {
            val ctorElem = (names.expectedElement as? PsiNamedElement)
            if (PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, ctor, ctorElem)) return null
            return Single(elementAtCaret, ctor, ctorElem)
        }
        return null
    }


}