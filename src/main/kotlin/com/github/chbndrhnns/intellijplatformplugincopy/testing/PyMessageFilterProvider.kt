package com.github.chbndrhnns.intellijplatformplugincopy.testing

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

class PyMessageFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> = arrayOf(
        PytestConsoleFilter(project)
    )
}
