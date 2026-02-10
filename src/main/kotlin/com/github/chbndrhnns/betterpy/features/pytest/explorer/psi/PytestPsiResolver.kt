package com.github.chbndrhnns.betterpy.features.pytest.explorer.psi

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

object PytestPsiResolver {

    private val LOG = Logger.getInstance(PytestPsiResolver::class.java)

    fun resolveTest(
        project: Project,
        test: CollectedTest,
    ): SmartPsiElementPointer<PyFunction>? = ReadAction.compute<SmartPsiElementPointer<PyFunction>?, Throwable> {
        LOG.debug("Resolving test: ${test.nodeId}")
        val psiFile = findPyFile(project, test.modulePath) ?: run {
            LOG.debug("Could not find file for test: ${test.modulePath}")
            return@compute null
        }

        val function = if (test.className != null) {
            val pyClass = psiFile.findTopLevelClass(test.className)
                ?: PsiTreeUtil.findChildrenOfType(psiFile, PyClass::class.java)
                    .firstOrNull { it.name == test.className }
            pyClass?.findMethodByName(test.functionName, false, null)
        } else {
            psiFile.findTopLevelFunction(test.functionName)
        }

        function?.let {
            LOG.debug("Resolved test ${test.functionName} to PSI element")
            SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it)
        } ?: run {
            LOG.debug("Could not resolve test function: ${test.functionName} in ${test.modulePath}")
            null
        }
    }

    fun resolveFixture(
        project: Project,
        fixture: CollectedFixture,
    ): SmartPsiElementPointer<PyFunction>? = ReadAction.compute<SmartPsiElementPointer<PyFunction>?, Throwable> {
        LOG.debug("Resolving fixture: ${fixture.name} in ${fixture.definedIn}")
        val psiFile = findPyFile(project, fixture.definedIn) ?: run {
            LOG.debug("Could not find file for fixture: ${fixture.definedIn}")
            return@compute null
        }

        val function = psiFile.findTopLevelFunction(fixture.functionName)
            ?: PsiTreeUtil.findChildrenOfType(psiFile, PyFunction::class.java)
                .firstOrNull { it.name == fixture.functionName }

        function?.let {
            SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it)
        }
    }

    fun extractFixtureDepsFromPsi(function: PyFunction): List<String> {
        return function.parameterList.parameters
            .filter { it.name != "self" && it.name != "request" }
            .mapNotNull { it.name }
    }

    private fun findPyFile(project: Project, relativePath: String): PyFile? {
        // Strategy 1: Direct path resolution via basePath
        val basePath = project.basePath
        if (basePath != null) {
            val vFile = LocalFileSystem.getInstance()
                .findFileByPath("$basePath/$relativePath")
            if (vFile != null) {
                val psi = PsiManager.getInstance(project).findFile(vFile) as? PyFile
                if (psi != null) return psi
            }
        }

        // Strategy 2: Filename index fallback (works in test fixtures and when basePath doesn't match)
        val fileName = relativePath.substringAfterLast("/")
        val candidates = FilenameIndex.getVirtualFilesByName(
            fileName, GlobalSearchScope.projectScope(project)
        )
        val match = candidates.firstOrNull { it.path.endsWith(relativePath) }
            ?: candidates.firstOrNull { it.name == fileName }
        return match?.let { PsiManager.getInstance(project).findFile(it) as? PyFile }
    }
}
