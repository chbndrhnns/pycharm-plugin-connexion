package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState

internal object PytestFixtureFeatureToggle {
    fun isEnabled(): Boolean = PluginSettingsState.instance().state.enableAdvancedPytestFixtureFeatures
}
