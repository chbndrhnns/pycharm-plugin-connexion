package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.analysis

import com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapPlan
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Context for the wrap analysis strategy pipeline.
 */
data class AnalysisContext(
    val editor: Editor,
    val file: PsiFile,
    val element: PyExpression,
    val typeEval: TypeEvalContext
)

/**
 * Result of a strategy execution.
 */
sealed class StrategyResult {
    data class Found(val plan: WrapPlan) : StrategyResult()
    data class Skip(val reason: String) : StrategyResult()
    data object Continue : StrategyResult()
}
