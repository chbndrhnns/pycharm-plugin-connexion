package com.github.chbndrhnns.intellijplatformplugincopy.bootstrap.listeners

import com.github.chbndrhnns.intellijplatformplugincopy.features.search.PyProtocolImplementationsSearch
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent

/**
 * PSI tree change listener that invalidates the protocol implementation cache
 * when Python files are modified.
 *
 * This ensures that cached protocol implementation results are refreshed
 * when the codebase changes, preventing stale results.
 */
class ProtocolCacheInvalidationListener : PsiTreeChangeAdapter() {

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        invalidateIfPythonFile(event)
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
        invalidateIfPythonFile(event)
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        invalidateIfPythonFile(event)
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        invalidateIfPythonFile(event)
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
        invalidateIfPythonFile(event)
    }

    private fun invalidateIfPythonFile(event: PsiTreeChangeEvent) {
        val file = event.file ?: return
        if (file.fileType.name == "Python") {
            PyProtocolImplementationsSearch.invalidateCache()
        }
    }
}
