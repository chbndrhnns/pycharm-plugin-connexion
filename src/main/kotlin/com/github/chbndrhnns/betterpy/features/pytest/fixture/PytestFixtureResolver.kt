package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.index.PytestFixtureFileIndex
import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.search.PyClassInheritorsSearch
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Represents a link in the fixture resolution chain.
 */
data class FixtureLink(
    val fixtureFunction: PyFunction,
    val fixtureName: String,
    val importElement: PsiElement? = null
) {
    override fun toString(): String {
        return "FixtureLink(fixtureName='$fixtureName')"
    }
}

/**
 * Resolves pytest fixtures following pytest's precedence rules:
 * 1. Class scope (test class and parent classes)
 * 2. Module scope (same file)
 * 3. Imports (explicit and wildcard)
 * 4. conftest.py (walking up directory tree)
 * 5. Built-in fixtures
 */
object PytestFixtureResolver {
    private val LOG = Logger.getInstance(PytestFixtureResolver::class.java)

    /**
     * Find all fixture candidates for a given usage site, in pytest precedence order.
     */
    fun findFixtureChain(
        usageElement: PsiElement,
        fixtureName: String,
        context: TypeEvalContext
    ): List<FixtureLink> {
        if (LOG.isDebugEnabled) {
            val fileName = usageElement.containingFile?.name
            LOG.debug(
                "PytestFixtureResolver.findFixtureChain: fixtureName='$fixtureName', element=${usageElement::class.java.simpleName}, " +
                        "file='${fileName ?: "<unknown>"}'"
            )
        }
        val result = mutableListOf<FixtureLink>()
        val seen = LinkedHashSet<PyFunction>()

        // 1. Class scope fixtures
        val classFixtures = findClassScopeFixtures(usageElement, fixtureName, seen)
        result.addAll(classFixtures)
        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureResolver.findFixtureChain: class-scope fixtures=${classFixtures.size}")
        }

        // 2. Module scope fixtures
        val moduleFixtures = findModuleScopeFixtures(usageElement, fixtureName, seen)
        result.addAll(moduleFixtures)
        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureResolver.findFixtureChain: module-scope fixtures=${moduleFixtures.size}")
        }

        // 3. Import fixtures
        val importFixtures = findImportedFixtures(usageElement, fixtureName, context, seen)
        result.addAll(importFixtures)
        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureResolver.findFixtureChain: imported fixtures=${importFixtures.size}")
        }

        // 4. conftest.py fixtures
        val conftestFixtures = findConftestFixtures(usageElement, fixtureName, seen)
        result.addAll(conftestFixtures)
        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureResolver.findFixtureChain: conftest fixtures=${conftestFixtures.size}")
        }

        // Note: Built-in fixtures would require access to pytest's internal fixture registry
        // which is not easily accessible from the plugin. We skip this for now.

        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureResolver.findFixtureChain: resolved total=${result.size}")
        }
        return result
    }

    /**
     * Find fixtures defined in the test class or parent classes.
     */
    private fun findClassScopeFixtures(
        usageElement: PsiElement,
        fixtureName: String,
        seen: MutableSet<PyFunction>
    ): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()

        // Find the containing class
        val containingClass = PsiTreeUtil.getParentOfType(usageElement, PyClass::class.java)
            ?: return result

        // Check the class and its ancestors (MRO order)
        val classes = mutableListOf(containingClass)
        classes.addAll(containingClass.getAncestorClasses(null))

        for (cls in classes) {
            for (method in cls.methods) {
                if (!PytestFixtureUtil.isFixtureFunction(method)) {
                    continue
                }
                val name = PytestFixtureUtil.getFixtureName(method)
                if (name == fixtureName && seen.add(method)) {
                    result.add(FixtureLink(method, fixtureName))
                }
            }
        }

        return result
    }

    /**
     * Find fixtures defined in the same module.
     */
    private fun findModuleScopeFixtures(
        usageElement: PsiElement,
        fixtureName: String,
        seen: MutableSet<PyFunction>
    ): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()

        val file = usageElement.containingFile as? PyFile ?: return result

        // Look for top-level functions with the fixture decorator
        for (function in file.topLevelFunctions) {
            if (PytestFixtureUtil.isFixtureFunction(function)) {
                val name = PytestFixtureUtil.getFixtureName(function)
                if (name == fixtureName && seen.add(function)) {
                    result.add(FixtureLink(function, fixtureName))
                }
            }
        }

        return result
    }

    /**
     * Find fixtures brought in via imports.
     */
    private fun findImportedFixtures(
        usageElement: PsiElement,
        fixtureName: String,
        context: TypeEvalContext,
        seen: MutableSet<PyFunction>
    ): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()

        val file = usageElement.containingFile as? PyFile ?: return result

        // Check explicit imports: from x import my_fixture (or alias)
        for (importElement in file.importTargets) {
            val resolved = importElement.reference?.resolve()
            if (resolved is PyFunction && PytestFixtureUtil.isFixtureFunction(resolved)) {
                val name = PytestFixtureUtil.getFixtureName(resolved)
                if (name == fixtureName && seen.add(resolved)) {
                    result.add(FixtureLink(resolved, fixtureName, importElement))
                }
            }
        }

        // Check wildcard imports: from x import *
        for (importStatement in PsiTreeUtil.findChildrenOfType(file, PyFromImportStatement::class.java)) {
            if (importStatement.isStarImport) {
                // Resolve the imported module
                val importSource = importStatement.importSource
                val resolvedModule = importSource?.reference?.resolve()
                if (resolvedModule is PyFile) {
                    // Look for fixtures in the imported module
                    for (function in resolvedModule.topLevelFunctions) {
                        if (PytestFixtureUtil.isFixtureFunction(function)) {
                            val name = PytestFixtureUtil.getFixtureName(function)
                            if (name == fixtureName && seen.add(function)) {
                                result.add(FixtureLink(function, fixtureName, importStatement))
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Find fixtures in conftest.py files walking up the directory tree.
     */
    private fun findConftestFixtures(
        usageElement: PsiElement,
        fixtureName: String,
        seen: MutableSet<PyFunction>
    ): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()

        val file = usageElement.containingFile as? PyFile ?: return result
        val virtualFile = file.virtualFile ?: return result

        var currentDir = virtualFile.parent

        // Walk up the directory tree looking for conftest.py
        while (currentDir != null) {
            val conftestFile = currentDir.findChild("conftest.py")
            if (conftestFile != null && !conftestFile.isDirectory) {
                val psiFile = file.manager.findFile(conftestFile) as? PyFile
                if (psiFile != null) {
                    // Look for fixtures in this conftest
                    for (function in psiFile.topLevelFunctions) {
                        if (PytestFixtureUtil.isFixtureFunction(function)) {
                            val name = PytestFixtureUtil.getFixtureName(function)
                            if (name == fixtureName && seen.add(function)) {
                                result.add(FixtureLink(function, fixtureName))
                            }
                        }
                    }
                }
            }
            currentDir = currentDir.parent
        }

        return result
    }

    /**
     * Find all fixtures that override a given fixture declaration.
     * Used for "Show Implementations" functionality.
     */
    fun findOverridingFixtures(
        fixtureFunction: PyFunction,
        fixtureName: String
    ): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()

        // Determine the scope of the fixture
        val containingClass = PsiTreeUtil.getParentOfType(fixtureFunction, PyClass::class.java)
        val containingFile = fixtureFunction.containingFile as? PyFile

        when {
            // Class fixture: find overrides in subclasses
            containingClass != null -> {
                if (LOG.isDebugEnabled) {
                    LOG.debug("PytestFixtureResolver.findOverridingFixtures: fixtureName='$fixtureName', scope=class")
                }
                result.addAll(findSubclassFixtureOverrides(containingClass, fixtureName))
            }
            // Module fixture: find overrides in classes in same module and in conftest descendants
            containingFile != null && containingFile.name != "conftest.py" -> {
                if (LOG.isDebugEnabled) {
                    LOG.debug("PytestFixtureResolver.findOverridingFixtures: fixtureName='$fixtureName', scope=module")
                }
                result.addAll(findClassFixturesInModule(containingFile, fixtureName))
            }
            // conftest.py fixture: find overrides in descendant conftest files and test modules
            containingFile != null && containingFile.name == "conftest.py" -> {
                if (LOG.isDebugEnabled) {
                    LOG.debug("PytestFixtureResolver.findOverridingFixtures: fixtureName='$fixtureName', scope=conftest")
                }
                result.addAll(findDescendantConftestFixtures(containingFile, fixtureName))
            }
        }

        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureResolver.findOverridingFixtures: resolved total=${result.size}")
        }
        return result
    }

    /**
     * Find parent fixtures for "Go to Super" navigation from an overriding fixture.
     */
    fun findParentFixtures(
        fixtureFunction: PyFunction,
        fixtureName: String,
        context: TypeEvalContext
    ): List<FixtureLink> {
        val file = fixtureFunction.containingFile as? PyFile ?: return emptyList()
        val chain = if (file.name == "conftest.py") {
            findConftestFixtures(fixtureFunction, fixtureName, mutableSetOf())
        } else {
            findFixtureChain(fixtureFunction, fixtureName, context)
        }
        val result = chain.filter { it.fixtureFunction != fixtureFunction }
        if (LOG.isDebugEnabled) {
            LOG.debug(
                "PytestFixtureResolver.findParentFixtures: fixtureName='$fixtureName', file='${file.name}', parents=${result.size}"
            )
        }
        return result
    }

    private fun findSubclassFixtureOverrides(baseClass: PyClass, fixtureName: String): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()
        val project = baseClass.project
        val projectScope = GlobalSearchScope.projectScope(project)
        val inheritors = PyClassInheritorsSearch.search(baseClass, true).findAll()

        for (subclass in inheritors) {
            val vFile = subclass.containingFile.virtualFile ?: continue
            if (!projectScope.contains(vFile)) continue
            for (method in subclass.methods) {
                if (!PytestFixtureUtil.isFixtureFunction(method)) {
                    continue
                }
                val name = PytestFixtureUtil.getFixtureName(method)
                if (name == fixtureName) {
                    result.add(FixtureLink(method, fixtureName))
                }
            }
        }
        return result
    }

    private fun findClassFixturesInModule(file: PyFile, fixtureName: String): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()

        for (cls in file.topLevelClasses) {
            for (method in cls.methods) {
                if (!PytestFixtureUtil.isFixtureFunction(method)) {
                    continue
                }
                val name = PytestFixtureUtil.getFixtureName(method)
                if (name == fixtureName) {
                    result.add(FixtureLink(method, fixtureName))
                }
            }
        }

        return result
    }

    private fun findDescendantConftestFixtures(conftestFile: PyFile, fixtureName: String): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()

        val virtualFile = conftestFile.virtualFile ?: return result
        val parentDir = virtualFile.parent ?: return result
        val project = conftestFile.project
        val scope = GlobalSearchScopesCore.directoryScope(project, parentDir, true)
        val candidateFiles = PytestFixtureFileIndex.findFilesWithFixture(fixtureName, project, scope)

        for (candidate in candidateFiles) {
            if (candidate == virtualFile) continue
            if (candidate.name == "conftest.py") {
                collectFixturesFromFile(conftestFile, candidate, fixtureName, result)
                continue
            }
            if (candidate.extension == "py") {
                collectFixturesFromFile(conftestFile, candidate, fixtureName, result, restrictToTestFiles = true)
            }
        }

        return result
    }

    private fun collectFixturesFromFile(
        originFile: PyFile,
        virtualFile: VirtualFile,
        fixtureName: String,
        result: MutableList<FixtureLink>,
        restrictToTestFiles: Boolean = false
    ) {
        val psiFile = originFile.manager.findFile(virtualFile) as? PyFile ?: return
        if (restrictToTestFiles && !PytestNaming.isTestFile(psiFile)) return
        for (function in psiFile.topLevelFunctions) {
            if (!PytestFixtureUtil.isFixtureFunction(function)) {
                continue
            }
            val name = PytestFixtureUtil.getFixtureName(function)
            if (name == fixtureName) {
                result.add(FixtureLink(function, fixtureName))
            }
        }
    }
}
