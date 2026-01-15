package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.search.PyTestDetection
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

private val LOG = logger<IntroduceParameterObjectTarget>()

internal object IntroduceParameterObjectTarget {

    fun find(element: PsiElement): PyFunction? {
        return ParameterObjectTargetUtils.findTargetFunction(element)
    }

    fun isAvailable(element: PsiElement): Boolean {
        val function = find(element) ?: return false

        val virtualFile = function.containingFile.virtualFile
        if (virtualFile != null) {
            val fileIndex = ProjectFileIndex.getInstance(function.project)
            if (fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInLibrarySource(virtualFile)) {
                LOG.debug("isAvailable: Function is in library code")
                return false
            }
        }

        if (function.containingFile.name.endsWith(".pyi")) {
            LOG.debug("isAvailable: Function is in .pyi file")
            return false
        }

        if (PyTestDetection.isTestFunction(function)) {
            LOG.debug("isAvailable: Function is a test function")
            return false
        }
        if (PyTestDetection.isPytestFixture(function)) {
            LOG.debug("isAvailable: Function is a pytest fixture")
            return false
        }

        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }

        if (parameters.isEmpty()) {
            LOG.debug("isAvailable: No valid parameters for refactoring")
            return false
        }
        
        return true
    }
}