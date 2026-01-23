package com.github.chbndrhnns.betterpy.features.refactoring

import com.github.chbndrhnns.betterpy.core.services.PythonStdlibService
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.jetbrains.python.psi.PyFile

class PyShadowingStdlibRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        return element is PyFile && PluginSettingsState.instance().state.enableShadowingStdlibModuleInspection
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        if (element !is PyFile) return

        val baseName = newName.substringBeforeLast(".")
        val stdlibService = PythonStdlibService.getInstance(element.project)

        if (stdlibService.isStdlibModule(baseName, null)) {
            val result = Messages.showYesNoDialog(
                element.project,
                "The name '$newName' shadows a Python standard library module. Are you sure you want to rename it?",
                "Shadowing Standard Library Module",
                "Rename Anyway",
                "Cancel",
                Messages.getWarningIcon()
            )
            if (result != Messages.YES && result != Messages.OK) {
                throw com.intellij.openapi.progress.ProcessCanceledException()
            }
        }
    }
}
