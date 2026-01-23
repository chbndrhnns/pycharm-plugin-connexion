package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction

private val LOG = logger<InlineParameterObjectTarget>()

internal object InlineParameterObjectTarget {

    fun find(element: PsiElement): PyFunction? {
        return ParameterObjectTargetUtils.findTargetFunction(element)
    }

    fun isAvailable(element: PsiElement): Boolean {
        val function = find(element) ?: return false

        if (!ParameterObjectTargetUtils.isCommonRefactoringCandidate(function, LOG)) {
            return false
        }

        val hasInlineable = PyInlineParameterObjectProcessor.hasInlineableParameterObject(function)
        if (!hasInlineable) {
            LOG.debug("isAvailable: No inlineable parameter object found")
            return false
        }
        
        return true
    }
}
