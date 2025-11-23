package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedTypeInfo
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.apply.WrapApplier
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.preview.WrapPreview
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyImportService
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyWrapHeuristics
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

class WrapItemsWithExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {
    private var lastText: String = "Wrap items with expected type"

    private companion object {
        val PLAN_KEY: Key<WrapPlan> = Key.create("wrap.items.with.expected.plan")
    }

    private val imports = PyImportService()
    private val applier = WrapApplier(imports)
    private val previewBuilder = WrapPreview()

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun getText(): String = lastText

    override fun getFamilyName(): String = "Type mismatch wrapper (items)"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableWrapIntention) {
            editor.putUserData(PLAN_KEY, null)
            return false
        }
        val plan = analyzeAtCaret(project, editor, file) ?: run {
            editor.putUserData(PLAN_KEY, null)
            return false
        }
        // Only accept Elementwise plans
        if (plan !is Elementwise && plan !is ElementwiseUnionChoice) {
            editor.putUserData(PLAN_KEY, null)
            return false
        }

        editor.putUserData(PLAN_KEY, plan)
        lastText = when (plan) {
            is ElementwiseUnionChoice -> "Wrap items with expected union type…"
            is Elementwise -> "Wrap items with ${plan.itemCtorName}()"
            else -> "Wrap items with expected type"
        }
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val plan = editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file) ?: return

        when (plan) {
            is ElementwiseUnionChoice -> {
                // Not implemented
            }

            is Elementwise -> {
                val element = plan.element
                if (element is PyListLiteralExpression || element is PySetLiteralExpression || element is PyTupleExpression) {
                    applier.applyWrapAllItemsInLiteral(
                        project,
                        file,
                        element,
                        plan.itemCtorName,
                        plan.itemCtorElement
                    )
                } else {
                    applier.applyElementwise(
                        project,
                        file,
                        element,
                        plan.container,
                        plan.itemCtorName,
                        plan.itemCtorElement
                    )
                }
            }

            else -> {}
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val plan =
            editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file) ?: return IntentionPreviewInfo.EMPTY
        return when (plan) {
            is Elementwise -> {
                val element = plan.element
                if (element is PyListLiteralExpression || element is PySetLiteralExpression || element is PyTupleExpression) {
                    previewBuilder.buildWrapAllItemsInLiteral(
                        file,
                        element,
                        plan.itemCtorName,
                        plan.itemCtorElement
                    )
                } else {
                    previewBuilder.buildElementwise(
                        file,
                        plan.element,
                        plan.container,
                        plan.itemCtorName,
                        plan.itemCtorElement
                    )
                }
            }

            else -> IntentionPreviewInfo.EMPTY
        }
    }

    override fun startInWriteAction(): Boolean = true

    private fun analyzeAtCaret(project: Project, editor: Editor, file: PsiFile): WrapPlan? {
        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null

        if (elementAtCaret is PyTargetExpression) return null

        val containerItemTarget = PyTypeIntentions.findContainerItemAtCaret(editor, elementAtCaret) ?: elementAtCaret

        val parent = containerItemTarget.parent
        val isLiteral = parent is PyListLiteralExpression ||
                parent is PySetLiteralExpression ||
                parent is PyTupleExpression ||
                parent is PyDictLiteralExpression

        val containerExpr = if (isLiteral && parent is PyExpression) parent else elementAtCaret

        val expectedOuterContainerCtor = expectedOuterContainerCtor(containerExpr, context)

        if (expectedOuterContainerCtor != null) {
            // Dicts are not supported for item wrapping yet (ambiguous whether to wrap keys or values, and requires dict comprehension)
            if (expectedOuterContainerCtor.name == "dict") return null

            PyTypeIntentions.tryContainerItemCtor(containerItemTarget, context)?.let { ctor ->
                val suppressedContainers = setOf("list", "set", "tuple", "dict")
                if (!suppressedContainers.contains(ctor.name.lowercase())) {
                    // Check if ALREADY wrapped.
                    if (!PyWrapHeuristics.isAlreadyWrappedWith(containerItemTarget, ctor.name, ctor.symbol)) {
                        // Return Elementwise pointing to containerExpr
                        return Elementwise(containerExpr, expectedOuterContainerCtor.name, ctor.name, ctor.symbol)
                    }
                }
            }
        }

        return null
    }

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
}
