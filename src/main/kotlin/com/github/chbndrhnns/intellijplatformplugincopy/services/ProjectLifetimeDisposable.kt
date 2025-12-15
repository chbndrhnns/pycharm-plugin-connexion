package com.github.chbndrhnns.intellijplatformplugincopy.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class ProjectLifetimeDisposable : Disposable {
    override fun dispose() = Unit
}
