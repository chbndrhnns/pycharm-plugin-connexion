package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.CtorMatch
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedTypeInfo
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.apply.WrapApplier
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.preview.WrapPreview
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.ui.JbPopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyImportService
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyWrapHeuristics
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.UnionCandidates
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon

/**
 * Intention that wraps an expression causing a type mismatch with the expected type constructor.
 * Uses the Python type system APIs for robust type inference.
 */
class WrapWithExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {
    // Minimal retained state: dynamic text shown by the IDE, updated in isAvailable().
    private var lastText: String = "Wrap with expected type"

    private companion object {
        val PLAN_KEY: Key<WrapPlan> = Key.create("wrap.with.expected.plan")
    }

    private val imports = PyImportService()
    private val applier = WrapApplier(imports)
    private val previewBuilder = WrapPreview()

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun getText(): String = lastText

    override fun getFamilyName(): String = "Type mismatch wrapper"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        // Feature switch: hide intention entirely when disabled via settings
        if (!PluginSettingsState.instance().state.enableWrapIntention) {
            editor.putUserData(PLAN_KEY, null)
            lastText = "Wrap with expected type"
            return false
        }
        val plan = analyzeAtCaret(project, editor, file) ?: run {
            editor.putUserData(PLAN_KEY, null)
            lastText = "Wrap with expected type"
            return false
        }
        editor.putUserData(PLAN_KEY, plan)

        // Update dynamic text used by tests to locate the intention
        lastText = when (plan) {
            is UnionChoice -> "Wrap with expected union type…"
            is ElementwiseUnionChoice -> "Wrap with expected union type…"
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
                    render = { renderUnionLabel(it) },
                    onChosen = { chosen ->
                        applier.apply(project, file, plan.element, chosen.name, chosen.symbol)
                    }
                )
            }

            is ElementwiseUnionChoice -> {
                val candidates = plan.candidates
                val popupHost = WrapWithExpectedTypeIntentionHooks.popupHost ?: JbPopupHost()
                popupHost.showChooser(
                    editor = editor,
                    title = "Select expected type",
                    items = candidates,
                    render = { renderUnionLabel(it) },
                    onChosen = { chosen ->
                        applier.applyElementwise(
                            project,
                            file,
                            plan.element,
                            plan.container,
                            chosen.name,
                            chosen.symbol
                        )
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
        if (!PluginSettingsState.instance().state.enableWrapIntention) {
            return IntentionPreviewInfo.EMPTY
        }
        val plan =
            editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file) ?: return IntentionPreviewInfo.EMPTY
        return when (plan) {
            is UnionChoice -> IntentionPreviewInfo.EMPTY
            is ElementwiseUnionChoice -> IntentionPreviewInfo.EMPTY
            is Single -> {
                previewBuilder.build(file, plan.element, plan.ctorName, plan.ctorElement)
            }

            is Elementwise -> previewBuilder.buildElementwise(
                file,
                plan.element,
                plan.container,
                plan.itemCtorName,
                plan.itemCtorElement
            )
        }
    }

    override fun startInWriteAction(): Boolean = true

    private fun analyzeAtCaret(project: Project, editor: Editor, file: PsiFile): WrapPlan? {
        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null

        // Do not offer wrapping on the variable name being defined/assigned to
        if (elementAtCaret is PyTargetExpression) return null

        // fix outer-most container mismatch first (list/set/tuple/dict + common aliases).
        // Guard: do NOT trigger when the caret sits inside an already-correct container literal/comprehension.
        expectedOuterContainerCtor(elementAtCaret, context)?.let { outerCtor ->
            // Skip if the element itself is already a literal/comprehension of the same outer container
            // (e.g., list literal when list[...] is expected). We'll handle inner items instead.
            val isSameContainerLiteral = when (outerCtor.name.lowercase()) {
                "list" -> elementAtCaret is PyListLiteralExpression || elementAtCaret is PyListCompExpression
                "set" -> elementAtCaret is PySetLiteralExpression || elementAtCaret is PySetCompExpression
                "dict" -> elementAtCaret is PyDictLiteralExpression || elementAtCaret is PyDictCompExpression
                "tuple" -> elementAtCaret is PyTupleExpression
                else -> false
            }
            // Prefer element-wise plan when outer container is already satisfied by the argument's actual type
            // (even if it's a variable, not a literal/comprehension), and we can infer the item ctor.
            val isContainerLiteral = when (elementAtCaret) {
                is PyListLiteralExpression, is PySetLiteralExpression, is PyDictLiteralExpression,
                is PyListCompExpression, is PySetCompExpression, is PyDictCompExpression, is PyTupleExpression -> true

                else -> false
            }
            if (!isContainerLiteral) {
                // Fix: dicts are not supported for item wrapping
                if (outerCtor.name == "dict") return null

                val match = PyTypeIntentions.elementDisplaysAsCtor(elementAtCaret, outerCtor.name, context)
                val names = PyTypeIntentions.computeDisplayTypeNames(elementAtCaret, context)
                val actualUsesSameOuter = names.actual?.lowercase()?.startsWith(outerCtor.name.lowercase()) == true
                if (match == CtorMatch.MATCHES || actualUsesSameOuter) {
                    val candidates = PyTypeIntentions.expectedItemCtorsForContainer(elementAtCaret, context)
                    if (candidates.isNotEmpty()) {
                        if (candidates.size >= 2) {
                            return ElementwiseUnionChoice(elementAtCaret, outerCtor.name, candidates)
                        }
                        val only = candidates.first()
                        if (!PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, only.name, only.symbol)) {
                            return Elementwise(elementAtCaret, outerCtor.name, only.name, only.symbol)
                        }
                    }
                }
            }

            // Fallback: if outer container differs, propose wrapping with the container ctor.
            if (!isSameContainerLiteral && !isInsideMatchingContainer(elementAtCaret, outerCtor.name)) {
                val differs =
                    PyTypeIntentions.elementDisplaysAsCtor(elementAtCaret, outerCtor.name, context) == CtorMatch.DIFFERS
                val alreadyWrapped =
                    PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, outerCtor.name, outerCtor.symbol)
                if (differs && !alreadyWrapped) {
                    val target: PyExpression = (elementAtCaret.parent as? PyExpression) ?: elementAtCaret
                    return Single(target, outerCtor.name, outerCtor.symbol)
                }
            }
        }

        // Prefer container-element analysis (e.g., items inside list literals) before generic logic.
        // When the caret expression is a container literal, locate the specific item under the caret
        // and target that element for wrapping; otherwise keep the original element.
        val containerItemTarget = PyTypeIntentions.findContainerItemAtCaret(editor, elementAtCaret)
            ?: elementAtCaret

        // Attempt element-level container analysis first; it only triggers when inside a container.
        PyTypeIntentions.tryContainerItemCtor(containerItemTarget, context)
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
                        // Prefer elementwise plan/label when expected outer container is present
                        // but only when the caret-targeted item is NOT inside a literal container.
                        val outer = expectedOuterContainerCtor(elementAtCaret, context)
                        if (outer != null) {
                            if (outer.name == "dict") return null

                            val p = containerItemTarget.parent
                            val isLiteral =
                                p is PyListLiteralExpression || p is PySetLiteralExpression || p is PyTupleExpression || p is PyDictLiteralExpression
                            if (!isLiteral) {
                                return Elementwise(containerItemTarget, outer.name, ctor.name, ctor.symbol)
                            }
                        }
                        return Single(containerItemTarget, ctor.name, ctor.symbol)
                    }
                }
            }
        // After handling element-level cases, fall back to union/generic ctor logic below.

        // Suppress generic wrapping when caret is on container literals where only element-level makes sense.
        if (elementAtCaret is PyListLiteralExpression ||
            elementAtCaret is PySetLiteralExpression ||
            elementAtCaret is PyDictLiteralExpression
        ) return null

        val names = PyTypeIntentions.computeDisplayTypeNames(elementAtCaret, context)
        if (names.actual == null || names.expected == null) return null
        // Allow wrapping if both types are Unknown (unresolved), provided we can find a valid constructor name later.
        if (names.actual == names.expected && names.actual != "Unknown") return null

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
            // Do not offer container constructors (list/set/tuple/dict) when the caret is inside any container
            // literal/comprehension — element-level wrapping should handle those cases.
            val suppressedContainerCtor = when (ctor.lowercase()) {
                "list", "set", "tuple", "dict" -> {
                    val p = elementAtCaret.parent
                    p is PyListLiteralExpression ||
                            p is PySetLiteralExpression ||
                            p is PyTupleExpression ||
                            p is PyDictLiteralExpression ||
                            p is PyListCompExpression ||
                            p is PySetCompExpression ||
                            p is PyDictCompExpression
                }

                else -> false
            }
            if (suppressedContainerCtor) return null
            val ctorElem = names.expectedElement
            if (PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, ctor, ctorElem)) return null
            return Single(elementAtCaret, ctor, ctorElem)
        }
        return null
    }

    /**
     * Render label for union choice popup items.
     * - If the symbol has a qualified name (classes, NewType aliases, functions, etc.),
     *   include it in parentheses to disambiguate (e.g., `User (a.User)`, `One (a.One)`).
     * - Otherwise, fall back to the simple name.
     */
    private fun renderUnionLabel(ctor: ExpectedCtor): String {
        val qNameOwner = ctor.symbol as? PyQualifiedNameOwner
        val fqn = qNameOwner?.qualifiedName
        return if (!fqn.isNullOrBlank()) "${ctor.name} ($fqn)" else ctor.name
    }

    /** Detect the expected outer container and map to concrete Python constructors we can call (list/set/tuple/dict). */
    private fun expectedOuterContainerCtor(expr: PyExpression, ctx: TypeEvalContext): ExpectedCtor? {
        val info = ExpectedTypeInfo
            .getExpectedTypeInfo(expr, ctx) ?: return null
        val annExpr = info.annotationExpr as? PyExpression ?: return null

        val baseName = when (val sub = annExpr as? PySubscriptionExpression) {
            null -> (annExpr as? PyReferenceExpression)?.name?.lowercase()
            else -> (sub.operand as? PyReferenceExpression)?.name?.lowercase()
        } ?: return null

        fun mapToConcrete(name: String): String? = when (name) {
            "list", "sequence", "collection", "iterable", "mutablesequence" -> "list"
            "set" -> "set"
            "tuple" -> "tuple"
            "dict", "mapping", "mutablemapping" -> "dict"
            else -> null
        }

        val concrete = mapToConcrete(baseName) ?: return null
        return ExpectedCtor(concrete, null)
    }

    /** True if the element is inside a literal/comprehension whose outer container already matches expected. */
    private fun isInsideMatchingContainer(element: PyExpression, expectedCtor: String): Boolean {
        val p = element.parent
        return when (expectedCtor.lowercase()) {
            "list" -> p is PyListLiteralExpression || p is PyListCompExpression
            "set" -> p is PySetLiteralExpression || p is PySetCompExpression
            "tuple" -> p is PyTupleExpression
            "dict" -> p is PyDictLiteralExpression || p is PyDictCompExpression
            else -> false
        }
    }


}