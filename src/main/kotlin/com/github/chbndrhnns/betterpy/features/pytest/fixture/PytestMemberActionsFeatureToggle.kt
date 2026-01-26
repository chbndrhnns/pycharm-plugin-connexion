package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState

internal object PytestMemberActionsFeatureToggle {
    fun isEnabled(): Boolean = PluginSettingsState.instance().state.enableNewPytestMemberActions
}
