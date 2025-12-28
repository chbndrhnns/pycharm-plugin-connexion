package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.JbPopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.ExpectedTypeAnalyzer
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.PyImportService
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.WrapApplier
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.WrapPreview
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
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon

/**
 * Intention that wraps an expression causing a type mismatch with the expected type constructor.
 * Uses the Python type system APIs for robust type inference.
 */
class WrapWithExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {
    // Minimal retained state: dynamic text shown by the IDE, updated in isAvailable().
    private var lastText: String = PluginConstants.ACTION_PREFIX + "Wrap with expected type"

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
        if (!PluginSettingsState.instance().state.enableWrapWithExpectedTypeIntention) {
            editor.putUserData(PLAN_KEY, null)
            lastText = PluginConstants.ACTION_PREFIX + "Wrap with expected type"
            return false
        }

        val elementAtCaret =
            PyTypeIntentions.findExpressionAtCaret(
                editor,
                file
            )
        if (elementAtCaret?.parent is PyStarArgument) {
            editor.putUserData(PLAN_KEY, null)
            lastText = PluginConstants.ACTION_PREFIX + "Wrap with expected type"
            return false
        }

        val analyzer = ExpectedTypeAnalyzer()
        val context = TypeEvalContext.codeAnalysis(project, file)
        val plan = analyzer.analyzeAtCaret(editor, file, context) ?: run {
            editor.putUserData(PLAN_KEY, null)
            lastText = PluginConstants.ACTION_PREFIX + "Wrap with expected type"
            return false
        }
        editor.putUserData(PLAN_KEY, plan)

        // Update dynamic text used by tests to locate the intention
        lastText = when (plan) {
            is UnionChoice -> PluginConstants.ACTION_PREFIX + "Wrap with expected union type…"
            is ElementwiseUnionChoice -> PluginConstants.ACTION_PREFIX + "Wrap with expected union type…"
            is Single -> plan.ctorName.takeIf { it.isNotBlank() }
                ?.let { PluginConstants.ACTION_PREFIX + "Wrap with $it()" }
                ?: (PluginConstants.ACTION_PREFIX + "Wrap with expected type")

            is Elementwise -> PluginConstants.ACTION_PREFIX + "Wrap items with ${plan.itemCtorName}()"
            is ReplaceWithVariant -> PluginConstants.ACTION_PREFIX + "Wrap with ${plan.variantName}"
            is LambdaWrap -> PluginConstants.ACTION_PREFIX + "Wrap with lambda"
        }
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val analyzer = ExpectedTypeAnalyzer()
        val context = TypeEvalContext.userInitiated(project, file)
        val plan = editor.getUserData(PLAN_KEY) ?: analyzer.analyzeAtCaret(editor, file, context) ?: return

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

            is ReplaceWithVariant -> {
                applier.applyVariant(project, file, plan.element, plan.variantName, plan.variantElement)
            }

            is LambdaWrap -> {
                applier.applyLambda(project, file, plan.element, plan.params)
            }
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        if (!PluginSettingsState.instance().state.enableWrapWithExpectedTypeIntention) {
            return IntentionPreviewInfo.EMPTY
        }
        val analyzer = ExpectedTypeAnalyzer()
        val context = TypeEvalContext.codeAnalysis(project, file)
        val plan =
            editor.getUserData(PLAN_KEY) ?: analyzer.analyzeAtCaret(editor, file, context)
            ?: return IntentionPreviewInfo.EMPTY
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

            is ReplaceWithVariant -> previewBuilder.buildVariant(
                file,
                plan.element,
                plan.variantName,
                plan.variantElement
            )

            is LambdaWrap -> previewBuilder.buildLambda(
                file,
                plan.element,
                plan.params
            )
        }
    }

    override fun startInWriteAction(): Boolean = true

    /**
     * Render label for union choice popup items.
     * - If the symbol has a qualified name (classes, NewType aliases, functions, etc.),
     *   include it in parentheses to disambiguate (e.g., , ).
     * - Otherwise, fall back to the simple name.
     */
    private fun renderUnionLabel(ctor: ExpectedCtor): String {
        val qNameOwner = ctor.symbol as? PyQualifiedNameOwner
        val fqn = qNameOwner?.qualifiedName
        return if (!fqn.isNullOrBlank()) "${ctor.name} ($fqn)" else ctor.name
    }
}
