package com.github.chbndrhnns.betterpy.features.pytest.inlinefixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.features.pytest.fixture.PytestFixtureResolver
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

class InlineFixtureAnalyzer(
    private val project: Project,
    private val file: PyFile
) {
    fun analyze(element: PsiElement): InlineFixtureModel? {
        val fixtureFunction = resolveFixtureFunction(element) ?: return null
        val fixtureName = PytestFixtureUtil.getFixtureName(fixtureFunction) ?: return null

        val body = fixtureFunction.statementList.statements.toList()
        val dependencies = fixtureFunction.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .mapNotNull { it.name }
            .filter { it != "self" && it != "cls" }

        val returnAnalysis = analyzeReturnValue(body)
        val usages = findFixtureUsages(fixtureName, fixtureFunction)

        val fixtureDecorator = PytestFixtureUtil.findFixtureDecorators(fixtureFunction).firstOrNull()
        val argumentList = fixtureDecorator?.argumentList
        val isAutouse = argumentList?.getKeywordArgument("autouse")?.let { arg ->
            val value = arg.valueExpression
            value?.text == "True"
        } ?: false
        val scope = PytestFixtureUtil.getFixtureScope(fixtureFunction)
        val isParametrized = argumentList?.getKeywordArgument("params") != null

        return InlineFixtureModel(
            fixtureFunction = fixtureFunction,
            fixtureName = fixtureName,
            body = body,
            dependencies = dependencies,
            returnInfo = returnAnalysis.returnInfo,
            usages = usages,
            isYieldFixture = returnAnalysis.isYieldFixture,
            isAutouse = isAutouse,
            scope = scope,
            isParametrized = isParametrized,
            hasMultipleReturns = returnAnalysis.hasMultipleReturns
        )
    }

    private fun resolveFixtureFunction(element: PsiElement): PyFunction? {
        val parameter = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java, false)
        if (parameter != null && isFixtureParameter(parameter)) {
            val fixtureName = parameter.name ?: return null
            val context = TypeEvalContext.codeAnalysis(project, parameter.containingFile)
            val chain = resolveFixtureChain(parameter, fixtureName, context)
            return chain.firstOrNull()?.fixtureFunction
        }

        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
        if (function != null && PytestFixtureUtil.isFixtureFunction(function)) {
            return function
        }

        return null
    }

    private data class ReturnAnalysis(
        val returnInfo: ReturnInfo,
        val hasMultipleReturns: Boolean,
        val isYieldFixture: Boolean
    )

    private fun analyzeReturnValue(body: List<PyStatement>): ReturnAnalysis {
        var returnStatement: PyStatement? = null
        var returnExpression: PyExpression? = null
        var yieldStatement: PyStatement? = null
        var yieldExpression: PyExpression? = null
        var returnCount = 0
        var yieldCount = 0

        for (statement in body) {
            when (statement) {
                is PyReturnStatement -> {
                    returnCount += 1
                    if (returnStatement == null) {
                        returnStatement = statement
                        returnExpression = statement.expression
                    }
                }

                is PyExpressionStatement -> {
                    val expr = statement.expression
                    if (expr is PyYieldExpression) {
                        yieldCount += 1
                        if (yieldStatement == null) {
                            yieldStatement = statement
                            yieldExpression = expr.expression
                        }
                    }
                }
            }
        }

        val isYieldFixture = yieldStatement != null
        val effectiveReturnStatement = yieldStatement ?: returnStatement
        val effectiveReturnExpression = yieldExpression ?: returnExpression

        val statementsBeforeReturn = if (effectiveReturnStatement != null) {
            body.takeWhile { it != effectiveReturnStatement }
        } else {
            body
        }

        val statementsAfterYield = if (yieldStatement != null) {
            body.dropWhile { it != yieldStatement }.drop(1)
        } else {
            emptyList()
        }

        val returnInfo = ReturnInfo(
            returnedExpression = effectiveReturnExpression,
            returnStatement = effectiveReturnStatement,
            statementsBeforeReturn = statementsBeforeReturn,
            statementsAfterYield = statementsAfterYield
        )

        val hasMultipleReturns = returnCount + yieldCount > 1
        return ReturnAnalysis(returnInfo, hasMultipleReturns, isYieldFixture)
    }

    private fun findFixtureUsages(
        fixtureName: String,
        fixtureFunction: PyFunction
    ): List<FixtureUsage> {
        val usages = LinkedHashSet<FixtureUsage>()
        val searchHelper = PsiSearchHelper.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        searchHelper.processElementsWithWord(
            { element, _ ->
                val elementFile = element.containingFile ?: return@processElementsWithWord true
                if (!elementFile.viewProvider.isPhysical) return@processElementsWithWord true

                val parameter = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java, false)
                if (parameter != null && parameter.isValid && parameter.isPhysical) {
                    val name = parameter.name
                    if (name == fixtureName && isFixtureParameter(parameter)) {
                        val context = TypeEvalContext.codeAnalysis(project, parameter.containingFile)
                        val chain = resolveFixtureChain(parameter, fixtureName, context)
                        val resolved = chain.firstOrNull()?.fixtureFunction
                        if (resolved != null && resolved.isEquivalentTo(fixtureFunction)) {
                            val usageFile = parameter.containingFile as? PyFile
                            val usageFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java)
                            if (usageFile != null && usageFunction != null) {
                                usages.add(FixtureUsage(usageFunction, parameter, usageFile))
                            }
                        }
                    }
                }

                val stringLiteral = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression::class.java, false)
                if (stringLiteral != null && stringLiteral.stringValue == fixtureName) {
                    val decorator = PsiTreeUtil.getParentOfType(stringLiteral, PyDecorator::class.java, false)
                    if (decorator != null && isUsefixturesDecorator(decorator)) {
                        val usageFunction = PsiTreeUtil.getParentOfType(decorator, PyFunction::class.java)
                        if (usageFunction != null && isTestOrFixtureFunction(usageFunction)) {
                            val context = TypeEvalContext.codeAnalysis(project, stringLiteral.containingFile)
                            val chain = resolveFixtureChain(stringLiteral, fixtureName, context)
                            val resolved = chain.firstOrNull()?.fixtureFunction
                            if (resolved != null && resolved.isEquivalentTo(fixtureFunction)) {
                                val usageFile = stringLiteral.containingFile as? PyFile
                                if (usageFile != null) {
                                    usages.add(
                                        FixtureUsage(
                                            function = usageFunction,
                                            parameter = null,
                                            file = usageFile,
                                            usefixturesArgument = stringLiteral,
                                            usefixturesDecorator = decorator
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                true
            },
            scope,
            fixtureName,
            UsageSearchContext.IN_CODE,
            true
        )

        return usages.toList()
    }

    private fun resolveFixtureChain(
        usageElement: PsiElement,
        fixtureName: String,
        context: TypeEvalContext
    ): List<com.github.chbndrhnns.betterpy.features.pytest.fixture.FixtureLink> {
        val chain = PytestFixtureResolver.findFixtureChain(usageElement, fixtureName, context)
        val parameter = usageElement as? PyNamedParameter
        if (parameter != null) {
            val containingFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java)
            val paramName = parameter.name
            if (containingFunction != null && paramName != null) {
                val containingFixtureName = PytestFixtureUtil.getFixtureName(containingFunction)
                if (containingFixtureName == paramName) {
                    return chain.filterNot { it.fixtureFunction.isEquivalentTo(containingFunction) }
                }
            }
        }
        return chain
    }

    private fun isFixtureParameter(parameter: PyNamedParameter): Boolean {
        val name = parameter.name ?: return false
        if (name == "self" || name == "cls") return false
        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return false
        return isTestOrFixtureFunction(function)
    }

    private fun isTestOrFixtureFunction(function: PyFunction): Boolean {
        return PytestNaming.isTestFunction(function) || PytestFixtureUtil.isFixtureFunction(function)
    }

    private fun isUsefixturesDecorator(decorator: PyDecorator): Boolean {
        val qualifiedName = decorator.callee?.let { (it as? PyQualifiedExpression)?.asQualifiedName()?.toString() }
        return qualifiedName == "pytest.mark.usefixtures" ||
                qualifiedName == "_pytest.mark.usefixtures" ||
                qualifiedName == "mark.usefixtures" ||
                qualifiedName?.endsWith(".mark.usefixtures") == true
    }
}
