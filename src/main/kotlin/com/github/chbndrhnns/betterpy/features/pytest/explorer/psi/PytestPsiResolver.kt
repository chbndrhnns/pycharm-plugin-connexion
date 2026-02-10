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

        val function = resolveFunction(psiFile, test)

        function?.let { fn ->
            LOG.debug("Resolved test ${test.functionName} to PSI element")
            SmartPointerManager.getInstance(project).createSmartPsiElementPointer(fn)
        } ?: run {
            LOG.debug("Could not resolve test function: ${test.functionName} in ${test.modulePath}")
            null
        }
    }

    fun resolveTestElement(
        project: Project,
        test: CollectedTest,
    ): PyFunction? = ReadAction.compute<PyFunction?, Throwable> {
        LOG.debug("Resolving test element: ${test.nodeId}")
        val psiFile = findPyFile(project, test.modulePath) ?: return@compute null

        resolveFunction(psiFile, test)
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

        function?.let { fn ->
            SmartPointerManager.getInstance(project).createSmartPsiElementPointer(fn)
        }
    }

    fun resolveFixtureElement(
        project: Project,
        fixture: CollectedFixture,
    ): PyFunction? = ReadAction.compute<PyFunction?, Throwable> {
        LOG.debug("Resolving fixture element: ${fixture.name} in ${fixture.definedIn}")
        val psiFile = findPyFile(project, fixture.definedIn) ?: return@compute null

        psiFile.findTopLevelFunction(fixture.functionName)
            ?: PsiTreeUtil.findChildrenOfType(psiFile, PyFunction::class.java)
                .firstOrNull { it.name == fixture.functionName }
    }

    /**
     * Resolves a test function by walking the full class chain from nodeId.
     * For "t.py::Test1::Test2::test_func", walks Test1 -> Test2 -> test_func.
     */
    private fun resolveFunction(psiFile: PyFile, test: CollectedTest): PyFunction? {
        val parts = test.nodeId.split("::")
        // parts[0] = module, last = function, middle = class chain
        val classChain = if (parts.size > 2) parts.subList(1, parts.size - 1) else emptyList()

        if (classChain.isEmpty()) {
            return psiFile.findTopLevelFunction(test.functionName)
        }

        // Walk the class chain
        var currentClass: PyClass? = psiFile.findTopLevelClass(classChain[0])
            ?: PsiTreeUtil.findChildrenOfType(psiFile, PyClass::class.java)
                .firstOrNull { it.name == classChain[0] }

        for (i in 1 until classChain.size) {
            currentClass = currentClass?.nestedClasses?.firstOrNull { it.name == classChain[i] }
        }

        return currentClass?.findMethodByName(test.functionName, false, null)
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
