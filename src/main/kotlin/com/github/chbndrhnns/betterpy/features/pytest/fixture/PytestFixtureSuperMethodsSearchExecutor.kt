package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext

class PytestFixtureSuperMethodsSearchExecutor :
    QueryExecutor<PsiElement, PySuperMethodsSearch.SearchParameters> {
    private val log = Logger.getInstance(PytestFixtureSuperMethodsSearchExecutor::class.java)

    override fun execute(
        queryParameters: PySuperMethodsSearch.SearchParameters,
        consumer: Processor<in PsiElement>
    ): Boolean {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return true
        }
        return ReadAction.compute<Boolean, RuntimeException> {
            val derived = queryParameters.derivedMethod
            if (!PytestFixtureUtil.isFixtureFunction(derived)) {
                return@compute true
            }

            val fixtureName = PytestFixtureUtil.getFixtureName(derived) ?: return@compute true
            val context = queryParameters.context
                ?: TypeEvalContext.codeAnalysis(derived.project, derived.containingFile)
            val parents = PytestFixtureResolver.findParentFixtures(
                derived,
                fixtureName,
                context
            )

            if (parents.isEmpty()) return@compute true
            if (log.isDebugEnabled) {
                log.debug(
                    "PytestFixtureSuperMethodsSearchExecutor: fixture '$fixtureName' has ${parents.size} parent(s), deep=${queryParameters.isDeepSearch}"
                )
            }

            if (!queryParameters.isDeepSearch) {
                return@compute consumer.process(parents.first().fixtureFunction)
            }

            for (link in parents) {
                if (!consumer.process(link.fixtureFunction)) {
                    return@compute false
                }
            }

            true
        }
    }
}
