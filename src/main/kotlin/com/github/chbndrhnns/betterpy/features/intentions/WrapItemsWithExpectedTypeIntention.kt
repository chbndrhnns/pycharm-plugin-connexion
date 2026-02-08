package com.github.chbndrhnns.betterpy.features.intentions

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.psi.PyImportService
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.intentions.shared.ExpectedCtor
import com.github.chbndrhnns.betterpy.features.intentions.shared.ExpectedTypeInfo
import com.github.chbndrhnns.betterpy.features.intentions.shared.PyTypeIntentions
import com.github.chbndrhnns.betterpy.features.intentions.wrap.PyWrapHeuristics
import com.github.chbndrhnns.betterpy.features.intentions.wrap.WrapApplier
import com.github.chbndrhnns.betterpy.features.intentions.wrap.WrapPreview
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
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon

class WrapItemsWithExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {
    private var lastText: String = PluginConstants.ACTION_PREFIX + "Wrap items with expected type"

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
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableWrapItemsWithExpectedTypeIntention) {
            editor.putUserData(PLAN_KEY, null)
            return false
        }

        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file)
        if (elementAtCaret?.parent is PyStarArgument) {
            editor.putUserData(PLAN_KEY, null)
            return false
        }

        val context = TypeEvalContext.codeAnalysis(project, file)
        val plan = analyzeAtCaret(project, editor, file, context) ?: run {
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
            is ElementwiseUnionChoice -> PluginConstants.ACTION_PREFIX + "Wrap items with expected union type…"
            is Elementwise -> PluginConstants.ACTION_PREFIX + "Wrap items with ${plan.itemCtorName}()"
        }
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val context = TypeEvalContext.userInitiated(project, file)
        val plan = editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file, context) ?: return

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
        val context = TypeEvalContext.codeAnalysis(project, file)
        val plan =
            editor.getUserData(PLAN_KEY) ?: analyzeAtCaret(project, editor, file, context)
            ?: return IntentionPreviewInfo.EMPTY
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

    private fun analyzeAtCaret(project: Project, editor: Editor, file: PsiFile, context: TypeEvalContext): WrapPlan? {
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null

        if (elementAtCaret is PyTargetExpression) return null
        if (elementAtCaret is PyNoneLiteralExpression) return null

        val containerItemTarget = PyTypeIntentions.findContainerItemAtCaret(editor, elementAtCaret) ?: elementAtCaret
        if (containerItemTarget is PyNoneLiteralExpression) return null

        val parent = containerItemTarget.parent
        val isLiteral = parent is PyListLiteralExpression ||
                parent is PySetLiteralExpression ||
                parent is PyTupleExpression ||
                parent is PyDictLiteralExpression

        val containerExpr = if (isLiteral) parent else elementAtCaret

        val expectedOuterContainerCtor = expectedOuterContainerCtor(containerExpr, context)

        if (expectedOuterContainerCtor != null) {
            // Dicts are not supported for item wrapping yet (ambiguous whether to wrap keys or values, and requires dict comprehension)
            if (expectedOuterContainerCtor.name == "dict") return null

            PyTypeIntentions.tryContainerItemCtor(containerItemTarget, context)?.let { ctor ->
                val suppressedContainers = setOf("list", "set", "tuple", "dict")
                if (!suppressedContainers.contains(ctor.name.lowercase())) {
                    // Check if ALREADY wrapped.
                    if (!PyWrapHeuristics.isAlreadyWrappedWith(containerItemTarget, ctor.name, ctor.symbol)) {
                        // Check if ALREADY compatible (subtype)
                        val itemType = context.getType(containerItemTarget)
                        val ctorClass = ctor.symbol as? PyClass
                        if (itemType != null && ctorClass != null) {
                            val expectedType = PyClassTypeImpl(ctorClass, false)
                            if (PyTypeChecker.match(expectedType, itemType, context)) {
                                return@let
                            }
                        }

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
