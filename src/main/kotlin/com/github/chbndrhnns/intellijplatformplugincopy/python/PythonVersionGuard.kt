package com.github.chbndrhnns.intellijplatformplugincopy.python

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.legacy.PythonSdkUtil

/**
 * Centralized guard for minimum supported Python version across actions, intentions and inspections.
 * Minimum is Python 3.11.
 */
object PythonVersionGuard {
    private val minLevel: LanguageLevel = LanguageLevel.PYTHON311

    fun minVersionString(): String = minLevel.toPythonVersion()

    fun isSatisfied(project: Project?): Boolean {
        if (project == null) return false
        // Resolve via any module in the project
        val modules = com.intellij.openapi.module.ModuleManager.getInstance(project).modules
        val firstModule = modules.firstOrNull() ?: return false
        val sdk = PythonSdkUtil.findPythonSdk(firstModule) ?: return false
        val level = LanguageLevel.fromPythonVersion(sdk.versionString ?: return false) ?: return false
        return level.isAtLeast(minLevel)
    }

    fun isSatisfied(module: Module?): Boolean {
        if (module == null) return false
        val sdk = PythonSdkUtil.findPythonSdk(module) ?: return false
        val level = LanguageLevel.fromPythonVersion(sdk.versionString ?: return false) ?: return false
        return level.isAtLeast(minLevel)
    }

    /**
     * Prefer file’s effective level when available (handles per-file overrides).
     */
    fun isSatisfiedForElement(element: PsiElement?): Boolean {
        if (element == null) return false
        val file = element.containingFile as? PyFile ?: return false
        val level = file.languageLevel
        return level.isAtLeast(minLevel)
    }
}
