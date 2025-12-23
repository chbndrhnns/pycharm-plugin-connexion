package com.github.chbndrhnns.intellijplatformplugincopy.exports

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.JbPopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import javax.swing.Icon

class PyExportSymbolToTargetIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {
    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Export symbol to target..."
    override fun getFamilyName(): String = "Export symbol to target"

    var popupHost: PopupHost = JbPopupHost()

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun startInWriteAction(): Boolean = false

    private fun isSettingEnabled(): Boolean =
        PluginSettingsState.instance().state.enableExportSymbolToTargetIntention

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is PyFile) return false
        if (!isSettingEnabled()) return false

        val symbol = findTargetSymbol(editor, file) ?: return false

        if (PsiTreeUtil.getParentOfType(symbol, PyFunction::class.java, PyClass::class.java) != null) return false

        val name = symbol.name ?: return false

        // Ignore conftest.py
        if (file.name == "conftest.py") return false

        // Ignore test modules
        if (file.name.startsWith("test_")) return false

        // Ignore test functions
        if (symbol is PyFunction && name.startsWith("test_")) return false

        // Ignore test classes
        if (symbol is PyClass && name.startsWith("Test_")) return false

        if (isDunder(name)) return false

        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val symbol = findTargetSymbol(editor, file) ?: return
        val name = symbol.name ?: return

        val targets = findExportTargets(file as PyFile)
        if (targets.isEmpty()) return

        if (targets.size == 1) {
            exportToTarget(project, targets[0], name, file)
        } else {
            val targetPaths = targets.associateBy {
                if (it == file) {
                    it.name + " (current module)"
                } else {
                    it.virtualFile.path.substringAfter(project.basePath ?: "")
                }
            }

            popupHost.showChooserWithGreying(
                editor,
                "Select target for export",
                targetPaths.keys.toList(),
                { it },
                { path ->
                    runReadAction {
                        val target = targetPaths[path]
                        target != null && isAlreadyExported(target, name)
                    }
                },
                { path ->
                    val target = targetPaths[path]
                    if (target != null) {
                        exportToTarget(project, target, name, file)
                    }
                }
            )
        }
    }

    private fun findTargetSymbol(editor: Editor, file: PsiFile): PsiNamedElement? {
        val offset = editor.caretModel.offset
        val atCaret = file.findElementAt(offset) ?: return null
        val named = PsiTreeUtil.getParentOfType(
            atCaret,
            PyFunction::class.java,
            PyClass::class.java,
            PyTargetExpression::class.java
        ) as? PsiNamedElement ?: return null

        if (named is PsiNameIdentifierOwner) {
            val nameId = named.nameIdentifier
            if (nameId != null && (nameId === atCaret || PsiTreeUtil.isAncestor(nameId, atCaret, false))) {
                return named
            }
        }
        return null
    }

    private fun isDunder(name: String): Boolean = name.length >= 4 && name.startsWith("__") && name.endsWith("__")

    private fun findExportTargets(file: PyFile): List<PyFile> {
        val project = file.project
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
        val targets = mutableListOf<PyFile>()

        // Current file is also a target if it is not private
        if (!file.name.startsWith("_") || file.name == PyNames.INIT_DOT_PY) {
            targets.add(file)
        }

        var currentDir = file.containingDirectory
        while (currentDir != null && projectFileIndex.isInSourceContent(currentDir.virtualFile)) {
            if (currentDir.name.startsWith("_") && currentDir.name != PyNames.INIT_DOT_PY) {
                // Exclude private directories
            } else {
                val initFile = currentDir.findFile(PyNames.INIT_DOT_PY)
                if (initFile is PyFile && initFile != file) {
                    targets.add(initFile)
                }
            }

            // Move up
            val parentDir = currentDir.parentDirectory
            if (parentDir == null || !projectFileIndex.isInSourceContent(parentDir.virtualFile)) break
            currentDir = parentDir
        }

        return targets
    }

    private fun isAlreadyExported(file: PyFile, name: String): Boolean {
        val dunderAll = file.findTopLevelAttribute(PyNames.ALL) ?: return false
        val assignedValue = (dunderAll.parent as? PyAssignmentStatement)?.assignedValue
        if (assignedValue is PySequenceExpression) {
            return assignedValue.elements.any { (it as? PyStringLiteralExpression)?.stringValue == name }
        }
        return false
    }

    private fun exportToTarget(project: Project, targetFile: PyFile, name: String, sourceModule: PyFile) {
        WriteCommandAction.runWriteCommandAction(project, "Export Symbol to Target", null, {
            val source = if (targetFile == sourceModule) null else sourceModule
            PyAllExportUtil.ensureSymbolExported(project, targetFile, name, source)
        })
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY
}