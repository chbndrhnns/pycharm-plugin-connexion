package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.core.pytest.PytestParametrizeUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Provides "Go to Declaration" support for pytest fixture parameters.
 *
 * This handler intercepts Cmd+B / Ctrl+B on fixture parameters to ensure
 * correct resolution, particularly for the fixture override pattern where
 * a fixture parameter has the same name as its containing fixture function.
 * In that case, navigation should go to the parent fixture, not the
 * containing function itself.
 */
class PytestFixtureGotoDeclarationHandler : GotoDeclarationHandler {
    private val log = Logger.getInstance(PytestFixtureGotoDeclarationHandler::class.java)

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        if (!PytestFixtureFeatureToggle.isEnabled()) return null

        val parameter = PsiTreeUtil.getParentOfType(sourceElement, PyNamedParameter::class.java, false)
            ?: return null

        // Skip lambda parameters
        val parameterList = parameter.parent as? PyParameterList ?: return null
        if (parameterList.parent is PyLambdaExpression) return null

        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java)
            ?: return null

        if (!isTestOrFixtureFunction(function)) return null

        val paramName = parameter.name ?: return null
        if (paramName == "self" || paramName == "cls") return null

        // Skip parametrize parameters
        if (paramName in PytestParametrizeUtil.collectAllParametrizeNames(function)) return null

        val context = TypeEvalContext.userInitiated(sourceElement.project, sourceElement.containingFile)
        val chain = PytestFixtureResolver.findFixtureChain(parameter, paramName, context)

        // When a fixture parameter has the same name as its containing fixture (override pattern),
        // filter out the containing function so that navigation goes to the parent fixture.
        val filtered = if (PytestFixtureUtil.isFixtureFunction(function)) {
            chain.filter { !it.fixtureFunction.isEquivalentTo(function) }
        } else {
            chain
        }

        if (log.isDebugEnabled) {
            log.debug(
                "PytestFixtureGotoDeclarationHandler: param='$paramName', chain=${chain.size}, filtered=${filtered.size}"
            )
        }

        if (filtered.isEmpty()) return null

        return filtered.map { it.fixtureFunction }.toTypedArray()
    }

    private fun isTestOrFixtureFunction(function: PyFunction): Boolean {
        if (PytestNaming.isTestFunction(function)) return true
        return PytestFixtureUtil.isFixtureFunction(function)
    }
}
