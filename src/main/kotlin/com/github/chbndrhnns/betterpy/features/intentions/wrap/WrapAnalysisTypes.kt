package com.github.chbndrhnns.betterpy.features.intentions.wrap

import com.github.chbndrhnns.betterpy.features.intentions.WrapPlan
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Indicates the structural context of the caret position for strategy selection.
 * 
 * - ELEMENT_LEVEL: Caret is on an element inside a container literal (list, set, tuple, dict).
 *   In this context, element-level strategies (ContainerItemStrategy) should be prioritized.
 * - CONTAINER_LEVEL: Caret is on a container expression or a non-container value.
 *   In this context, container-level strategies (OuterContainerStrategy) should be prioritized.
 * - UNKNOWN: Context could not be determined; use default pipeline order.
 */
enum class StrategyContext {
    ELEMENT_LEVEL,
    CONTAINER_LEVEL,
    UNKNOWN
}

/**
 * Context for the wrap analysis strategy pipeline.
 */
data class AnalysisContext(
    val editor: Editor,
    val file: PsiFile,
    val element: PyExpression,
    val typeEval: TypeEvalContext,
    val strategyContext: StrategyContext = StrategyContext.UNKNOWN
)

/**
 * Result of a strategy execution.
 */
sealed class StrategyResult {
    data class Found(val plan: WrapPlan) : StrategyResult()
    data class Skip(val reason: String) : StrategyResult()
    data object Continue : StrategyResult()
}
