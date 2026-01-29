package com.github.chbndrhnns.betterpy.features.usages

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.usages.rules.UsageFilteringRuleProvider

class PyShowOnlyTestUsagesAction :
    ToggleAction(
        "Show test usages only",
        "Hide usages outside test sources",
        AllIcons.Nodes.Test
    ),
    DumbAware {
    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        return project.service<PyTestUsageFilteringStateService>().showOnlyTestUsages
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        project.service<PyTestUsageFilteringStateService>().showOnlyTestUsages = state
        project.messageBus.syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run()
    }
}
