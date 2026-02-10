package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.intellij.openapi.vfs.VirtualFile

/**
 * Manages the "pinned scope" state for the pytest explorer panel.
 *
 * When both follow-caret and scope-to-current-file are active and the user
 * navigates to a fixture that lives in a different module (e.g. conftest.py),
 * the scope is pinned to the original test file. This prevents the tree from
 * being rebuilt for the fixture's module. Follow and scope resume automatically
 * when the user opens a file that contains tests.
 */
class ScopePinManager(
    private val hasTestsForFile: (VirtualFile) -> Boolean,
) {
    var pinnedFile: VirtualFile? = null
        private set

    /**
     * Called when a fixture navigation opens [targetFile].
     * Pins the scope to [currentFile] if they differ and both follow + scope are active.
     */
    fun onFixtureNavigated(
        currentFile: VirtualFile?,
        targetFile: VirtualFile,
        scopeToCurrentFile: Boolean,
        followCaret: Boolean,
    ) {
        if (!scopeToCurrentFile || !followCaret) return
        val current = currentFile ?: return
        if (targetFile != current) {
            pinnedFile = current
        }
    }

    /**
     * Called when the editor selection changes to [newFile].
     * Returns `true` if the panel should proceed with normal scope/follow updates,
     * `false` if updates should be suppressed (scope is still pinned).
     */
    fun onEditorChanged(newFile: VirtualFile?): Boolean {
        if (pinnedFile == null) return true
        if (newFile != null && hasTestsForFile(newFile)) {
            pinnedFile = null
            return true
        }
        return false
    }

    /**
     * Returns the file to use for scope filtering: the pinned file if set,
     * otherwise `null` (caller should fall back to the current editor file).
     */
    fun scopeFile(): VirtualFile? = pinnedFile

    fun reset() {
        pinnedFile = null
    }
}
