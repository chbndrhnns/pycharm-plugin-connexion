package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.index.PyClassMembersFileIndex
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.StatusBar
import com.intellij.util.indexing.FileBasedIndex

/**
 * Action to manually trigger a rebuild of the [PyClassMembersFileIndex].
 * 
 * This action is available via "Search Everywhere" (double-shift) and can be used
 * to force a re-index of Python class members when needed.
 */
class RebuildClassMembersIndexAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        FileBasedIndex.getInstance().requestRebuild(PyClassMembersFileIndex.NAME)
        StatusBar.Info.set("Requested rebuild of Python class members index", e.project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        // Action is always available when a project is open
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
