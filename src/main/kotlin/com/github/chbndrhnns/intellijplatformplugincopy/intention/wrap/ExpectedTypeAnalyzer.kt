package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapPlan
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyTargetExpression
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
