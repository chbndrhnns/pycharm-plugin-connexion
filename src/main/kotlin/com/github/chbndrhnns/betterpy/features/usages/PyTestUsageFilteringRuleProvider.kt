package com.github.chbndrhnns.betterpy.features.usages

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.usages.UsageView
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.usages.rules.UsageFilteringRuleProvider

class PyTestUsageFilteringRuleProvider : UsageFilteringRuleProvider {
    override fun getApplicableRules(project: Project): MutableCollection<out UsageFilteringRule> {
        if (!PluginSettingsState.instance().state.enableTestUsageFilteringRule) {
            return mutableListOf()
        }
        return mutableListOf<UsageFilteringRule>(PyTestUsageFilteringRule(project))
    }

    @Deprecated("Deprecated in platform API")
    override fun getActiveRules(project: Project): Array<UsageFilteringRule> {
        if (!PluginSettingsState.instance().state.enableTestUsageFilteringRule) {
            return UsageFilteringRule.EMPTY_ARRAY
        }
        val state = project.service<PyTestUsageFilteringStateService>()
        if (!state.showOnlyTestUsages) return UsageFilteringRule.EMPTY_ARRAY
        return arrayOf(PyTestUsageFilteringRule(project))
    }

    @Deprecated("Deprecated in platform API")
    override fun createFilteringActions(usageView: UsageView): Array<com.intellij.openapi.actionSystem.AnAction> {
        return arrayOf(PyShowOnlyTestUsagesAction())
    }
}
