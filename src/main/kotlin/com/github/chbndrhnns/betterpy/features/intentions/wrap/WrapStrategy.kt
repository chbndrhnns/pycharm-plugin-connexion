package com.github.chbndrhnns.betterpy.features.intentions.wrap

import com.github.chbndrhnns.betterpy.features.intentions.shared.CtorMatch
import com.github.chbndrhnns.betterpy.features.intentions.shared.PyTypeIntentions
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Strategy interface for wrap intention analysis.
 */
interface WrapStrategy {
    fun run(context: AnalysisContext): StrategyResult
}

/**
 * Helper object for checking if unwrapping would yield the expected constructor type.
 */
object UnwrapStrategy {
    fun unwrapYieldsExpectedCtor(element: PyExpression, expectedCtorName: String, context: TypeEvalContext): Boolean {
        val wrapperInfo = PyWrapHeuristics.getWrapperCallInfo(element) ?: return false
        if (wrapperInfo.name.lowercase() in PyWrapHeuristics.CONTAINERS) return false

        val match = PyTypeIntentions.elementDisplaysAsCtor(wrapperInfo.inner, expectedCtorName, context)
        if (match != CtorMatch.MATCHES) return false

        val type = context.getType(element)
        if (type is PyClassType) {
            val cls = type.pyClass
            if (cls.name == expectedCtorName) return true
            val ancestors = cls.getAncestorClasses(context)
            if (ancestors.any { it.name == expectedCtorName }) return true
        }

        return true
    }
}
