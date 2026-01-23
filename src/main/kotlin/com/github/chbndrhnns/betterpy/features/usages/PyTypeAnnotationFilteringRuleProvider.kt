package com.github.chbndrhnns.betterpy.features.usages

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.project.Project
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.usages.rules.UsageFilteringRuleProvider

class PyTypeAnnotationFilteringRuleProvider : UsageFilteringRuleProvider {
    override fun getApplicableRules(project: Project): MutableCollection<out UsageFilteringRule> {
        if (!PluginSettingsState.instance().state.enableTypeAnnotationUsageFilteringRule) {
            return mutableListOf()
        }
        return mutableListOf<UsageFilteringRule>(PyTypeAnnotationFilteringRule(project))
    }
}