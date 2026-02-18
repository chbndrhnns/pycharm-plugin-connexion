package com.github.chbndrhnns.betterpy.features.pytest.inlinefixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.jetbrains.python.psi.*

/**
 * Model containing analysis results for inline fixture refactoring.
 */
data class InlineFixtureModel(
    val fixtureFunction: PyFunction,
    val fixtureName: String,
    val body: List<PyStatement>,
    val dependencies: List<String>,
    val returnInfo: ReturnInfo,
    val usages: List<FixtureUsage>,
    val isYieldFixture: Boolean,
    val isAutouse: Boolean = false,
    val scope: PytestFixtureUtil.PytestFixtureScope? = null,
    val isParametrized: Boolean = false,
    val hasMultipleReturns: Boolean = false
)

data class ReturnInfo(
    val returnedExpression: PyExpression?,
    val returnStatement: PyStatement?,
    val statementsBeforeReturn: List<PyStatement>,
    val statementsAfterYield: List<PyStatement>
)

data class FixtureUsage(
    val function: PyFunction,
    val parameter: PyNamedParameter?,
    val file: PyFile,
    val usefixturesArgument: PyStringLiteralExpression? = null,
    val usefixturesDecorator: PyDecorator? = null
)

enum class InlineMode {
    INLINE_ALL_AND_REMOVE,
    INLINE_THIS_ONLY
}

data class InlineFixtureOptions(
    val inlineMode: InlineMode = InlineMode.INLINE_ALL_AND_REMOVE
)
