package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project

class CopyStacktraceAction : AbstractCopyTestNodeAction("\n\n") {

    override fun getValuableText(proxy: SMTestProxy, project: Project): String? {
        if (proxy.isDefect) {
            return proxy.stacktrace
        }
        return null
    }
}
