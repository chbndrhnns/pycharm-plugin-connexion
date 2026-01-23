package com.github.chbndrhnns.betterpy.features.testing

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

class PyMessageFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> {
        if (!PluginSettingsState.instance().state.enablePyMessageConsoleFilter) {
            return emptyArray()
        }
        return arrayOf(
            PytestConsoleFilter(project),
            PythonClassConsoleFilter(project)
        )
    }
}
