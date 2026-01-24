package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction

private const val NAVIGATE_TO_OVERRIDING_FIXTURES = "Navigate to overriding fixtures"

class PytestFixtureLineMarkerProvider : RelatedItemLineMarkerProvider() {
    private val log = Logger.getInstance(PytestFixtureLineMarkerProvider::class.java)

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (!PytestFixtureFeatureToggle.isEnabled()) return
        if (element !is PyFunction) return
        if (!PytestFixtureUtil.isFixtureFunction(element)) return

        val fixtureName = PytestFixtureUtil.getFixtureName(element) ?: return
        val overridingFixtures = PytestFixtureResolver.findOverridingFixtures(element, fixtureName)
        if (overridingFixtures.isEmpty()) return

        if (log.isDebugEnabled) {
            log.debug(
                "PytestFixtureLineMarkerProvider: adding line marker for '$fixtureName' with ${overridingFixtures.size} override(s)"
            )
        }
        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
            .setTargets(overridingFixtures.map { it.fixtureFunction })
            .setTooltipText(NAVIGATE_TO_OVERRIDING_FIXTURES)

        result.add(builder.createLineMarkerInfo(element.nameIdentifier ?: element))
    }
}
