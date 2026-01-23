package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

private val LOG = logger<IntroduceParameterObjectTarget>()

internal object IntroduceParameterObjectTarget {

    fun find(element: PsiElement): PyFunction? {
        return ParameterObjectTargetUtils.findTargetFunction(element)
    }

    fun isAvailable(element: PsiElement): Boolean {
        val function = find(element) ?: return false

        if (!ParameterObjectTargetUtils.isCommonRefactoringCandidate(function, LOG)) {
            return false
        }

        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }

        if (parameters.isEmpty()) {
            LOG.debug("isAvailable: No valid parameters for refactoring")
            return false
        }
        
        return true
    }
}