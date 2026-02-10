package com.github.chbndrhnns.betterpy.features.pytest.explorer.trigger

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.PytestCollectorTask
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.*

/**
 * Startup activity that:
 * 1. Restores last snapshot from PytestExplorerService on project open.
 * 2. Triggers initial collection after a delay (to let indexing finish).
 * 3. Registers file-change listeners for automatic re-collection with debouncing.
 */
class PytestExplorerStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!PluginSettingsState.instance().state.enablePytestExplorer) {
            LOG.debug("Pytest Explorer is disabled, skipping startup activity")
            return
        }
        LOG.info("Pytest Explorer startup activity executing for project: ${project.name}")

        delay(INITIAL_DELAY_MS.toLong())
        triggerCollection(project)

        val connection: MessageBusConnection = project.messageBus.connect()
        val scope = CoroutineScope(currentCoroutineContext() + SupervisorJob(currentCoroutineContext()[Job]))
        var debounceJob: Job? = null

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (!PluginSettingsState.instance().state.enablePytestExplorer) return

                val hasTestFileChanges = events.any { event ->
                    PytestFileFilter.isTestRelatedFile(event.file?.name)
                }

                if (hasTestFileChanges) {
                    val changedNames =
                        events.mapNotNull { it.file?.name }.filter { PytestFileFilter.isTestRelatedFile(it) }
                    LOG.debug("Test-related file changes detected: $changedNames, scheduling re-collection in ${DEBOUNCE_DELAY_MS}ms")
                    debounceJob?.cancel()
                    scope.launch {
                        delay(DEBOUNCE_DELAY_MS.toLong())
                        triggerCollection(project)
                    }
                }
            }
        })
    }

    companion object {
        private val LOG = Logger.getInstance(PytestExplorerStartupActivity::class.java)
        const val INITIAL_DELAY_MS = 5000
        const val DEBOUNCE_DELAY_MS = 3000

        fun triggerCollection(project: Project) {
            if (project.isDisposed) return
            LOG.info("Triggering pytest collection for project: ${project.name}")
            PytestCollectorTask(project).queue()
        }
    }
}
