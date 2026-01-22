package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.WrapPlan
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.shared.PyTypeIntentions
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyNoneLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.TypeEvalContext

class ExpectedTypeAnalyzer() {

    // Base strategies that don't depend on context
    private val enumStrategies: List<WrapStrategy> = listOf(
        EnumMemberDefinitionStrategy(),
        EnumStrategy()
    )

    private val unionStrategy = UnionStrategy()
    private val callableStrategy = CallableStrategy()
    private val genericCtorStrategy = GenericCtorStrategy()
    private val outerContainerStrategy = OuterContainerStrategy()
    private val containerItemStrategy = ContainerItemStrategy()

    /**
     * Build the strategy pipeline based on the detected context.
     * 
     * When the caret is on an element inside a container literal (ELEMENT_LEVEL),
     * we prioritize ContainerItemStrategy to handle element-level type mismatches first.
     * 
     * When the caret is on a container or non-container value (CONTAINER_LEVEL),
     * we prioritize OuterContainerStrategy to handle container-level wrapping first.
     */
    private fun buildPipeline(strategyContext: StrategyContext): List<WrapStrategy> {
        val containerStrategies = when (strategyContext) {
            StrategyContext.ELEMENT_LEVEL -> listOf(containerItemStrategy, outerContainerStrategy)
            StrategyContext.CONTAINER_LEVEL -> listOf(outerContainerStrategy, containerItemStrategy)
            StrategyContext.UNKNOWN -> listOf(outerContainerStrategy, containerItemStrategy)
        }

        return containerStrategies + listOf(unionStrategy) + enumStrategies + listOf(
            callableStrategy,
            genericCtorStrategy
        )
    }

    fun analyzeAtCaret(editor: Editor, file: PsiFile, context: TypeEvalContext): WrapPlan? {
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null

        // Do not offer wrapping on the variable name being defined/assigned to
        if (elementAtCaret is PyTargetExpression) return null

        // Never offer wrap for None
        if (elementAtCaret is PyNoneLiteralExpression) return null

        val strategyContext = detectStrategyContext(elementAtCaret)
        val analysisContext = AnalysisContext(editor, file, elementAtCaret, context, strategyContext)
        val pipeline = buildPipeline(strategyContext)

        for (strategy in pipeline) {
            return when (val result = strategy.run(analysisContext)) {
                is StrategyResult.Found -> result.plan
                is StrategyResult.Skip -> null // Stop pipeline
                is StrategyResult.Continue -> continue // Try next strategy
            }
        }

        return null
    }

    /**
     * Detect the strategy context based on the element's position in the PSI tree.
     * 
     * Returns ELEMENT_LEVEL if the element is inside a container literal (list, set, tuple, dict).
     * Returns CONTAINER_LEVEL otherwise.
     */
    private fun detectStrategyContext(element: PyExpression): StrategyContext {
        return if (PyWrapHeuristics.isInsideContainerLiteralOrComprehension(element)) {
            StrategyContext.ELEMENT_LEVEL
        } else {
            StrategyContext.CONTAINER_LEVEL
        }
    }
}
