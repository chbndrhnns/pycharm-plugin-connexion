package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.analysis

import com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapPlan
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

class ExpectedTypeAnalyzer(private val project: Project) {
    private val pipeline: List<WrapStrategy> = listOf(
        OuterContainerStrategy(),
        ContainerItemStrategy(),
        UnionStrategy(),
        GenericCtorStrategy()
    )

    fun analyzeAtCaret(editor: Editor, file: PsiFile): WrapPlan? {
        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null

        // Do not offer wrapping on the variable name being defined/assigned to
        if (elementAtCaret is PyTargetExpression) return null

        // Heuristic: do not offer wrapping for arguments to builtin ``print``.
        //
        // For calls like ``print("a<caret>bc")`` the type stubs expose a very generic
        // expected type (e.g. ``object`` or a union around it). Offering wraps such as
        // "Wrap with object()" or "Wrap with (object, ...)()" is just noise and we have
        // explicit regression tests asserting that no wrap intention is shown when the
        // caret is inside the string literal argument to ``print``.
        if (elementAtCaret is PyExpression) {
            val argList = elementAtCaret.parent as? PyArgumentList
            val call = argList?.parent as? PyCallExpression
            val calleeName = (call?.callee as? PyReferenceExpression)?.name
            if (calleeName == "print") {
                return null
            }
        }

        val analysisContext = AnalysisContext(editor, file, elementAtCaret, context)

        for (strategy in pipeline) {
            return when (val result = strategy.run(analysisContext)) {
                is StrategyResult.Found -> result.plan
                is StrategyResult.Skip -> null // Stop pipeline
                is StrategyResult.Continue -> continue // Try next strategy
            }
        }

        return null
    }
}
