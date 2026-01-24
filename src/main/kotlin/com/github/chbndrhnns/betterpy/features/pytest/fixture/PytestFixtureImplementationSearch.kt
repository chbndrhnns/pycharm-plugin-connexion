package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.jetbrains.python.psi.PyFunction

/**
 * Provides "Show Implementations" support for pytest fixtures.
 * When the caret is on a fixture definition, this finds all overriding fixtures.
 */
class PytestFixtureImplementationSearch : QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
    private val log = Logger.getInstance(PytestFixtureImplementationSearch::class.java)

    override fun execute(
        queryParameters: DefinitionsScopedSearch.SearchParameters,
        consumer: Processor<in PsiElement>
    ): Boolean {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return true
        }
        return ReadAction.compute<Boolean, RuntimeException> {
            val element = queryParameters.element

            // Check if this is a fixture function
            if (element !is PyFunction) {
                return@compute true
            }

            if (!PytestFixtureUtil.isFixtureFunction(element)) {
                return@compute true
            }

            val fixtureName = PytestFixtureUtil.getFixtureName(element) ?: return@compute true

            // Find all overriding fixtures
            val overridingFixtures = PytestFixtureResolver.findOverridingFixtures(element, fixtureName)
            if (log.isDebugEnabled) {
                log.debug(
                    "PytestFixtureImplementationSearch: fixture '$fixtureName' has ${overridingFixtures.size} override(s)"
                )
            }

            // Process each overriding fixture
            for (link in overridingFixtures) {
                if (!consumer.process(link.fixtureFunction)) {
                    return@compute false
                }
            }

            true
        }
    }
}
