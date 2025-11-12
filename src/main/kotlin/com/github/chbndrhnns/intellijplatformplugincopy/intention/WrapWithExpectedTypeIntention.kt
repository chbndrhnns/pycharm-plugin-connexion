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

    // Element-wise wrapping plan (e.g., list[Item] expected, wrap as [Item(v) for v in src])
    private data class Elementwise(
        override val element: PyExpression,
        val container: String, // e.g., "list"
        val itemCtorName: String,
        val itemCtorElement: PsiNamedElement?
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

            is Elementwise -> "Wrap items with ${plan.itemCtorName}()"
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

            is Elementwise -> {
                applier.applyElementwise(
                    project,
                    file,
                    plan.element,
                    plan.container,
                    plan.itemCtorName,
                    plan.itemCtorElement
                )
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

            is Elementwise -> previewBuilder.buildElementwise(file, plan.element, plan.container, plan.itemCtorName)
        }
    }

    override fun startInWriteAction(): Boolean = true

    private fun analyzeAtCaret(project: Project, editor: Editor, file: PsiFile): WrapPlan? {
        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null

        // Special-case: when a tuple literal is provided where list[T] is expected, prefer list(tuple)
        // rather than element-wise mapping. This matches user expectations and tests.
        run {
            val ctor = expectedListItemCtor(elementAtCaret, context)
            val enclosingTuple = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                elementAtCaret,
                com.jetbrains.python.psi.PyTupleExpression::class.java
            )
            val tupleTarget = when {
                elementAtCaret is com.jetbrains.python.psi.PyTupleExpression -> elementAtCaret
                enclosingTuple != null -> enclosingTuple
                else -> null
            }
            if (ctor != null && tupleTarget != null) {
                if (!PyWrapHeuristics.isAlreadyWrappedWith(tupleTarget, "list", null)) {
                    return Single(tupleTarget, "list", null)
                }
            }
        }

        // Prefer container-element analysis (e.g., items inside list literals) before generic logic.
        // When the caret expression is a container literal, locate the specific item under the caret
        // and target that element for wrapping; otherwise keep the original element.
        val containerItemTarget = PyTypeIntentions.findContainerItemAtCaret(editor, elementAtCaret)
            ?: elementAtCaret

        // If we're inside a tuple literal but the expected container is a list[T], do NOT offer
        // element-level wrapping (e.g., int(...)) — we'll prefer list(tuple) below.
        val expectedListForArg = expectedListItemCtor(elementAtCaret, context) != null
        val insideTupleLiteral = elementAtCaret is com.jetbrains.python.psi.PyTupleExpression ||
                elementAtCaret.parent is com.jetbrains.python.psi.PyTupleExpression
        val suppressItemLevelDueToTupleVsList = expectedListForArg && insideTupleLiteral

        // Always attempt element-level container analysis: the helper discovers the enclosing container itself
        if (!suppressItemLevelDueToTupleVsList) PyTypeIntentions.tryContainerItemCtor(containerItemTarget, context)
            ?.let { ctor ->
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
        // Suppress generic wrapping when caret is on certain container literals where only element-level makes sense.
        // Note: allow tuple literals to enable converting tuple -> list (test expectation).
        if (elementAtCaret is com.jetbrains.python.psi.PyListLiteralExpression ||
            elementAtCaret is com.jetbrains.python.psi.PySetLiteralExpression ||
            elementAtCaret is com.jetbrains.python.psi.PyDictLiteralExpression
        ) return null

        // Try wrapping when expected type is list[T]
        expectedListItemCtor(elementAtCaret, context)?.let { ctor ->
            // If the source is already an iterable/container-like expression (e.g., set(), range(), comp),
            // or the caret is inside a tuple literal, prefer list(expr) over element-wise mapping.
            val containerish = PyWrapHeuristics.isContainerExpression(elementAtCaret) ||
                    elementAtCaret.parent is com.jetbrains.python.psi.PyTupleExpression
            if (containerish) {
                val targetForList: PyExpression = when (elementAtCaret) {
                    is com.jetbrains.python.psi.PyTupleExpression -> elementAtCaret
                    else -> (elementAtCaret.parent as? PyExpression) ?: elementAtCaret
                }
                if (!PyWrapHeuristics.isAlreadyWrappedWith(targetForList, "list", null)) {
                    return Single(targetForList, "list", null)
                }
            } else {
                // If the source is a scalar literal, prefer a singleton list over element-wise mapping.
                when (elementAtCaret) {
                    is com.jetbrains.python.psi.PyStringLiteralExpression,
                    is com.jetbrains.python.psi.PyNumericLiteralExpression,
                    is com.jetbrains.python.psi.PyBoolLiteralExpression -> {
                        if (!PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, "list", null)) {
                            return Single(elementAtCaret, "list", null)
                        }
                    }
                }
                // Otherwise, offer element-wise wrapping: [T(v) for v in expr]
                if (!PyWrapHeuristics.isAlreadyWrappedWith(
                        elementAtCaret,
                        ctor.name,
                        ctor.symbol
                    )
                ) {
                    return Elementwise(
                        elementAtCaret,
                        container = "list",
                        itemCtorName = ctor.name,
                        itemCtorElement = ctor.symbol
                    )
                }
            }
        }

        val names = PyTypeIntentions.computeDisplayTypeNames(elementAtCaret, context)
        if (names.actual == null || names.expected == null || names.actual == names.expected) return null

        val ctor = PyTypeIntentions.expectedCtorName(elementAtCaret, context)
        val annElement = names.expectedAnnotationElement
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

    /** Minimal helper to detect expected list[T] item ctor from the expected annotation. */
    private fun expectedListItemCtor(expr: PyExpression, ctx: TypeEvalContext): ExpectedCtor? {
        val info = PyTypeIntentions.computeDisplayTypeNames(expr, ctx)
        val annExpr = info.expectedAnnotationElement as? com.jetbrains.python.psi.PyExpression ?: return null
        val sub = annExpr as? com.jetbrains.python.psi.PySubscriptionExpression ?: return null

        val baseName = (sub.operand as? com.jetbrains.python.psi.PyReferenceExpression)
            ?.name?.lowercase() ?: return null
        if (baseName != "list") return null

        val idx = sub.indexExpression as? com.jetbrains.python.psi.PyExpression ?: return null
        val inner = when (idx) {
            is com.jetbrains.python.psi.PyTupleExpression -> idx.elements.firstOrNull()
            else -> idx
        } as? com.jetbrains.python.psi.PyTypedElement ?: return null

        val ctorName = PyTypeIntentions.canonicalCtorName(inner, ctx) ?: return null
        val sym = (inner as? com.jetbrains.python.psi.PyReferenceExpression)
            ?.reference?.resolve() as? PsiNamedElement
        return ExpectedCtor(ctorName, sym)
    }


}