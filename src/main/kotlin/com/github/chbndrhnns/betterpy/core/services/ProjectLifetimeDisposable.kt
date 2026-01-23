package com.github.chbndrhnns.betterpy.core.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class ProjectLifetimeDisposable : Disposable {
    override fun dispose() = Unit
}
