package com.github.chbndrhnns.betterpy.features.actions

import com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import java.awt.datatransfer.StringSelection

private val LOG = logger<CopyPackageContentAction>()

class CopyPackageContentAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // Determine the root for relative path calculation (e.g., Content Root or Project Root)
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val root = fileIndex.getContentRootForFile(selectedFile) ?: project.basePath?.let {
            LocalFileSystem.getInstance().findFileByPath(it)
        }

        val filesToCopy = mutableListOf<VirtualFile>()

        // Recursively collect all files
        VfsUtilCore.visitChildrenRecursively(selectedFile, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isDirectory) {
                    filesToCopy.add(file)
                }
                return true
            }
        })

        // Sort files by their path to ensure deterministic output
        filesToCopy.sortBy { it.path }

        val sb = StringBuilder()
        for (file in filesToCopy) {
            // Calculate relative path for the header
            val relativePath = if (root != null) {
                VfsUtilCore.getRelativePath(file, root) ?: file.name
            } else {
                file.path
            }

            sb.append("# $relativePath\n")

            try {
                // Read file content using the file's specific charset
                val text = String(file.contentsToByteArray(), file.charset)
                sb.append(text)
                // Ensure there is a newline between files
                if (!text.endsWith("\n")) {
                    sb.append("\n")
                }
            } catch (ex: Exception) {
                LOG.warn("Failed to read file content: ${file.path}", ex)
                sb.append("# [Error reading file: ${ex.message}]\n")
            }
            sb.append("\n")
        }

        // Copy to clipboard
        CopyPasteManager.getInstance().setContents(StringSelection(sb.toString()))
    }

    override fun update(e: AnActionEvent) {
        // Only enable the action if a directory is selected
        if (!PluginSettingsState.instance().state.enableCopyPackageContentAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        if (!PythonVersionGuard.isSatisfied(e.project)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
