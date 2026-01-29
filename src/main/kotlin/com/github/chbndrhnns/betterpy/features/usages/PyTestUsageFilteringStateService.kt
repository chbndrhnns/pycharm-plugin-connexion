package com.github.chbndrhnns.betterpy.features.usages

import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class PyTestUsageFilteringStateService {
    var showOnlyTestUsages: Boolean = false
}
