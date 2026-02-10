package com.github.chbndrhnns.betterpy.features.pytest.explorer.api

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Extension point for third-party plugins to contribute additional fixture sources.
 * E.g., pytest-django fixtures, factory_boy, etc.
 */
interface FixtureProvider {
    fun getAdditionalFixtures(project: Project): List<CollectedFixture>

    companion object {
        val EP_NAME = ExtensionPointName<FixtureProvider>(
            "com.github.chbndrhnns.betterpy.pytestFixtureProvider"
        )
    }
}
