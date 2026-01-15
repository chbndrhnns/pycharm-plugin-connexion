package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.search.PyTestDetection
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

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
                return false
            }
        }

        if (function.containingFile.name.endsWith(".pyi")) return false

        if (PyTestDetection.isTestFunction(function)) return false
        if (PyTestDetection.isPytestFixture(function)) return false

        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }

        return parameters.isNotEmpty()
    }
}