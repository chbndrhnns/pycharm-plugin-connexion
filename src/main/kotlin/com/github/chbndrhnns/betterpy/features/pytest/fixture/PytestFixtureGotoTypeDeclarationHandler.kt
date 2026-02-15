package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.core.pytest.PytestParametrizeUtil
import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.*

/**
 * Provides "Go to Type Declaration" support for pytest fixture parameters.
 *
 * When the caret is on a fixture parameter (in a test or fixture function signature),
 * this handler resolves the fixture's return type and navigates to the class declaration
 * of that type.
 */
class PytestFixtureGotoTypeDeclarationHandler : TypeDeclarationProvider {
    private val log = Logger.getInstance(PytestFixtureGotoTypeDeclarationHandler::class.java)

    override fun getSymbolTypeDeclarations(symbol: PsiElement): Array<PsiElement>? {
        if (!PytestFixtureFeatureToggle.isEnabled()) return null

        val parameter = symbol as? PyNamedParameter ?: return null

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

        val context = TypeEvalContext.userInitiated(symbol.project, symbol.containingFile)
        val chain = PytestFixtureResolver.findFixtureChain(parameter, paramName, context)

        // When a fixture parameter has the same name as its containing fixture (override pattern),
        // filter out the containing function so that navigation goes to the parent fixture.
        val filtered = if (PytestFixtureUtil.isFixtureFunction(function)) {
            chain.filter { !it.fixtureFunction.isEquivalentTo(function) }
        } else {
            chain
        }

        if (filtered.isEmpty()) return null

        // Resolve the return type of the first (highest-precedence) fixture
        val fixtureFunction = filtered.first().fixtureFunction
        val returnType = context.getReturnType(fixtureFunction) ?: return null

        val classes = mutableSetOf<PyClass>()
        collectClasses(returnType, classes)

        if (log.isDebugEnabled) {
            log.debug(
                "PytestFixtureGotoTypeDeclarationHandler: param='$paramName', returnType=$returnType, classes=${classes.map { it.name }}"
            )
        }

        if (classes.isEmpty()) return null

        return classes.toTypedArray()
    }

    private fun collectClasses(type: PyType?, classes: MutableSet<PyClass>) {
        when (type) {
            is PyUnionType -> type.members.forEach { collectClasses(it, classes) }
            is PyCollectionType -> {
                val pyClass = type.pyClass
                if (pyClass != null) classes.add(pyClass)
            }
            is PyClassType -> {
                val pyClass = type.pyClass
                classes.add(pyClass)
            }
        }
    }

    private fun isTestOrFixtureFunction(function: PyFunction): Boolean {
        if (PytestNaming.isTestFunction(function)) return true
        return PytestFixtureUtil.isFixtureFunction(function)
    }
}
