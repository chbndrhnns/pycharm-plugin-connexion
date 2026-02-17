package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction

/**
 * Suppresses PyMethodOverriding inspection for pytest fixtures.
 * 
 * Pytest fixtures can have different signatures when overriding because
 * they can inject other fixtures as parameters.
 */
class PytestFixtureInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "PyMethodOverriding") return false

        val function = element.parent as? PyFunction ?: return false
        return PytestFixtureUtil.isFixtureFunction(function)
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
        return SuppressQuickFix.EMPTY_ARRAY
    }
}
