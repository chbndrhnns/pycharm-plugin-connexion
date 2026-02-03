package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.index.PytestFixtureFileIndex
import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.search.PyClassInheritorsSearch
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PythonSdkUtil

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
            val fileName = usageElement.containingFile?.virtualFile?.path ?: "<unknown>"
            LOG.debug(
                "PytestFixtureResolver.findFixtureChain: fixtureName='$fixtureName', element=${usageElement::class.java.simpleName}, " +
                        "file='$fileName'"
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

        // 5. pytest plugin fixtures from entry points
        val pluginFixtures = findPluginFixtures(usageElement, fixtureName, seen)
        result.addAll(pluginFixtures)
        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureResolver.findFixtureChain: plugin fixtures=${pluginFixtures.size}")
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
        collectAssignedFixtures(file, fixtureName, seen, result)

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
            if (LOG.isDebugEnabled) {
                val visibleName = importElement.asName ?: importElement.importedQName?.lastComponent
                LOG.debug(
                    "PytestFixtureResolver.findImportedFixtures: importTarget='${importElement.text}', visibleName='${visibleName ?: "<unknown>"}'"
                )
            }
            val resolveResults = importElement.multiResolve()
            if (resolveResults.isNotEmpty()) {
                resolveResults.mapNotNull { it.element }.forEach { resolved ->
                    if (LOG.isDebugEnabled) {
                        LOG.debug(
                            "PytestFixtureResolver.findImportedFixtures: resolved='${resolved::class.java.simpleName}'"
                        )
                    }
                    if (resolved is PyFunction && PytestFixtureUtil.isFixtureFunction(resolved)) {
                        val name = PytestFixtureUtil.getFixtureName(resolved)
                        if (name == fixtureName && seen.add(resolved)) {
                            result.add(FixtureLink(resolved, fixtureName, importElement))
                        }
                    }
                    if (resolved is PyTargetExpression) {
                        val assignment = resolved.parent as? PyAssignmentStatement
                        val assignedFixture = assignment?.let { PytestFixtureUtil.getAssignedFixture(it) }
                        if (assignedFixture != null && LOG.isDebugEnabled) {
                            LOG.debug(
                                "PytestFixtureResolver.findImportedFixtures: assignedFixture='${assignedFixture.fixtureName}' " +
                                        "from '${assignedFixture.fixtureFunction.name}'"
                            )
                        }
                        if (assignedFixture != null &&
                            assignedFixture.fixtureName == fixtureName &&
                            seen.add(assignedFixture.fixtureFunction)
                        ) {
                            result.add(FixtureLink(assignedFixture.fixtureFunction, fixtureName, importElement))
                        }
                    }
                }
            } else {
                val resolved = importElement.reference?.resolve()
                if (resolved is PyFunction && PytestFixtureUtil.isFixtureFunction(resolved)) {
                    val name = PytestFixtureUtil.getFixtureName(resolved)
                    if (name == fixtureName && seen.add(resolved)) {
                        result.add(FixtureLink(resolved, fixtureName, importElement))
                    }
                }
                if (resolved is PyTargetExpression) {
                    val assignment = resolved.parent as? PyAssignmentStatement
                    val assignedFixture = assignment?.let { PytestFixtureUtil.getAssignedFixture(it) }
                    if (assignedFixture != null && LOG.isDebugEnabled) {
                        LOG.debug(
                            "PytestFixtureResolver.findImportedFixtures: assignedFixture='${assignedFixture.fixtureName}' " +
                                    "from '${assignedFixture.fixtureFunction.name}'"
                        )
                    }
                    if (assignedFixture != null &&
                        assignedFixture.fixtureName == fixtureName &&
                        seen.add(assignedFixture.fixtureFunction)
                    ) {
                        result.add(FixtureLink(assignedFixture.fixtureFunction, fixtureName, importElement))
                    }
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
                    if (LOG.isDebugEnabled) {
                        LOG.debug(
                            "PytestFixtureResolver.findImportedFixtures: star import from '${resolvedModule.name}'"
                        )
                    }
                    // Look for fixtures in the imported module
                    for (function in resolvedModule.topLevelFunctions) {
                        if (PytestFixtureUtil.isFixtureFunction(function)) {
                            val name = PytestFixtureUtil.getFixtureName(function)
                            if (name == fixtureName && seen.add(function)) {
                                result.add(FixtureLink(function, fixtureName, importStatement))
                            }
                        }
                    }
                    collectAssignedFixtures(resolvedModule, fixtureName, seen, result, importStatement)
                }
            }
        }

        // Check explicit from-imports where direct resolution may be missing
        for (fromImport in file.fromImports) {
            if (fromImport.isStarImport) continue
            val resolvedModule = fromImport.importSource?.reference?.resolve() as? PyFile ?: continue
            for (importElement in fromImport.importElements) {
                val visibleName = importElement.asName ?: importElement.importedQName?.lastComponent
                if (visibleName != fixtureName) continue
                if (LOG.isDebugEnabled) {
                    LOG.debug(
                        "PytestFixtureResolver.findImportedFixtures: from-import module='${resolvedModule.name}', name='${visibleName}'"
                    )
                }
                collectAssignedFixtures(resolvedModule, fixtureName, seen, result, importElement)
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
        val pluginRoots = buildPluginRoots(usageElement, file.project)

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
                    collectAssignedFixtures(psiFile, fixtureName, seen, result)
                    val pluginModules = extractPytestPluginModules(psiFile)
                    collectPluginFixturesFromModules(
                        pluginModules,
                        pluginRoots,
                        fixtureName,
                        seen,
                        result,
                        file.project
                    )
                }
            }
            currentDir = currentDir.parent
        }

        return result
    }

    private fun findPluginFixtures(
        usageElement: PsiElement,
        fixtureName: String,
        seen: MutableSet<PyFunction>
    ): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()
        val project = usageElement.project
        if (DumbService.isDumb(project)) return result

        val roots = buildPluginRoots(usageElement, project)
        val sdkRoots = findSdkRoots(usageElement, project)

        if (LOG.isDebugEnabled) {
            LOG.debug(
                "PytestFixtureResolver.findPluginFixtures: roots=${roots.size}, sdkRoots=${sdkRoots.size}"
            )
        }

        val entryModules = mutableListOf<String>()
        for (root in roots) {
            val distInfos = root.children.filter { it.isDirectory && it.name.endsWith(".dist-info") }
            for (distInfo in distInfos) {
                val entryPointsFile = distInfo.findChild("entry_points.txt") ?: continue
                val modules = parsePytestEntryPointModules(entryPointsFile)
                if (modules.isEmpty()) continue
                if (LOG.isDebugEnabled) {
                    LOG.debug(
                        "PytestFixtureResolver.findPluginFixtures: distInfo='${distInfo.name}', modules=${modules.size}"
                    )
                }
                entryModules.addAll(modules)
            }

            val pyprojectModules = collectPyprojectModules(root)
            if (pyprojectModules.isNotEmpty() && LOG.isDebugEnabled) {
                LOG.debug(
                    "PytestFixtureResolver.findPluginFixtures: pyprojectModules=${pyprojectModules.size}"
                )
            }
            entryModules.addAll(pyprojectModules)
        }
        collectPluginFixturesFromModules(entryModules, roots, fixtureName, seen, result, project)

        return result
    }

    private fun findSdkRoots(
        usageElement: PsiElement,
        project: com.intellij.openapi.project.Project
    ): Set<VirtualFile> {
        val module = ModuleUtilCore.findModuleForPsiElement(usageElement)
            ?: ModuleManager.getInstance(project).modules.firstOrNull()
        val sdk = module?.let { PythonSdkUtil.findPythonSdk(it) }
        return sdkRoots(sdk)
    }

    private fun sdkRoots(sdk: Sdk?): Set<VirtualFile> {
        if (sdk == null) return emptySet()
        val result = mutableSetOf<VirtualFile>()
        sdk.rootProvider.getFiles(com.intellij.openapi.roots.OrderRootType.CLASSES).forEach { result.add(it) }
        sdk.rootProvider.getFiles(com.intellij.openapi.roots.OrderRootType.SOURCES).forEach { result.add(it) }
        return result
    }

    private fun buildPluginRoots(
        usageElement: PsiElement,
        project: com.intellij.openapi.project.Project
    ): Set<VirtualFile> {
        val roots = mutableSetOf<VirtualFile>()
        val classRoots = OrderEnumerator.orderEntries(project)
            .librariesOnly()
            .classes()
            .roots
        for (root in classRoots) {
            roots.add(root)
        }
        val sourceRoots = OrderEnumerator.orderEntries(project)
            .librariesOnly()
            .sources()
            .roots
        for (root in sourceRoots) {
            roots.add(root)
        }
        roots.addAll(findSdkRoots(usageElement, project))
        project.guessProjectDir()?.let { roots.add(it) }
        return roots
    }

    private fun parsePytestEntryPointModules(entryPointsFile: VirtualFile): List<String> {
        val text = runCatching { VfsUtilCore.loadText(entryPointsFile) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<String>()
        var inPytestSection = false
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) return@forEach
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inPytestSection = trimmed.substring(1, trimmed.length - 1) == "pytest11"
                return@forEach
            }
            if (!inPytestSection) return@forEach
            val parts = trimmed.split("=", limit = 2)
            if (parts.size != 2) return@forEach
            val value = parts[1].trim()
            if (value.isEmpty()) return@forEach
            val module = value.substringBefore(":").trim()
            if (module.isNotEmpty()) {
                result.add(module)
            }
        }
        return result.distinct()
    }

    private fun collectPyprojectModules(root: VirtualFile): List<String> {
        val result = mutableListOf<String>()
        val rootPyproject = root.findChild("pyproject.toml")
        if (rootPyproject != null) {
            result.addAll(parsePytestModulesFromPyproject(rootPyproject))
        }
        root.children.filter { it.isDirectory }.forEach { dir ->
            val pyproject = dir.findChild("pyproject.toml") ?: return@forEach
            result.addAll(parsePytestModulesFromPyproject(pyproject))
        }
        return result.distinct()
    }

    private fun parsePytestModulesFromPyproject(pyprojectFile: VirtualFile): List<String> {
        val text = runCatching { VfsUtilCore.loadText(pyprojectFile) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<String>()
        var inPytestSection = false
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val section = trimmed.substring(1, trimmed.length - 1)
                inPytestSection = section == "project.entry-points.\"pytest11\"" ||
                        section == "project.entry-points.'pytest11'" ||
                        section == "project.entry-points.pytest11" ||
                        section == "tool.poetry.plugins.\"pytest11\"" ||
                        section == "tool.poetry.plugins.'pytest11'" ||
                        section == "tool.poetry.plugins.pytest11"
                return@forEach
            }
            if (!inPytestSection) return@forEach
            val parts = trimmed.split("=", limit = 2)
            if (parts.size != 2) return@forEach
            val value = parts[1].trim().trim('"', '\'')
            if (value.isEmpty()) return@forEach
            val module = value.substringBefore(":").trim()
            if (module.isNotEmpty()) {
                result.add(module)
            }
        }
        return result.distinct()
    }

    private fun collectFixturesFromModuleFile(
        moduleFile: PyFile,
        fixtureName: String,
        seen: MutableSet<PyFunction>,
        result: MutableList<FixtureLink>
    ) {
        for (function in moduleFile.topLevelFunctions) {
            if (!PytestFixtureUtil.isFixtureFunction(function)) continue
            val name = PytestFixtureUtil.getFixtureName(function)
            if (name == fixtureName && seen.add(function)) {
                result.add(FixtureLink(function, fixtureName))
            }
        }
        collectAssignedFixtures(moduleFile, fixtureName, seen, result)
    }

    private fun collectFixturesFromPackage(
        moduleFile: PyFile,
        fixtureName: String,
        seen: MutableSet<PyFunction>,
        result: MutableList<FixtureLink>
    ) {
        val packageDir = moduleFile.virtualFile?.parent ?: return
        val project = moduleFile.project
        val scope = GlobalSearchScopesCore.directoryScope(project, packageDir, true)
        val candidates = PytestFixtureFileIndex.findFilesWithFixture(fixtureName, project, scope)
        for (candidate in candidates) {
            val psiFile = PsiManager.getInstance(project).findFile(candidate) as? PyFile ?: continue
            for (function in psiFile.topLevelFunctions) {
                if (!PytestFixtureUtil.isFixtureFunction(function)) continue
                val name = PytestFixtureUtil.getFixtureName(function)
                if (name == fixtureName && seen.add(function)) {
                    result.add(FixtureLink(function, fixtureName))
                }
            }
            for (pyClass in psiFile.topLevelClasses) {
                for (method in pyClass.methods) {
                    if (!PytestFixtureUtil.isFixtureFunction(method)) continue
                    val name = PytestFixtureUtil.getFixtureName(method)
                    if (name == fixtureName && seen.add(method)) {
                        result.add(FixtureLink(method, fixtureName))
                    }
                }
            }
            collectAssignedFixtures(psiFile, fixtureName, seen, result)
        }
    }

    private fun collectPluginFixturesFromModules(
        moduleNames: List<String>,
        roots: Collection<VirtualFile>,
        fixtureName: String,
        seen: MutableSet<PyFunction>,
        result: MutableList<FixtureLink>,
        project: com.intellij.openapi.project.Project
    ) {
        if (moduleNames.isEmpty()) return
        val queue = ArrayDeque(moduleNames)
        val processed = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val moduleName = queue.removeFirst()
            if (!processed.add(moduleName)) continue
            val moduleFile = resolveModuleFileInRoots(project, roots, moduleName) ?: continue
            if (LOG.isDebugEnabled) {
                LOG.debug(
                    "PytestFixtureResolver.collectPluginFixturesFromModules: module='$moduleName', file='${moduleFile.name}'"
                )
            }
            collectFixturesFromModuleFile(moduleFile, fixtureName, seen, result)
            collectFixturesFromPackage(moduleFile, fixtureName, seen, result)
            val plugins = extractPytestPluginModules(moduleFile)
            if (plugins.isNotEmpty() && LOG.isDebugEnabled) {
                LOG.debug(
                    "PytestFixtureResolver.collectPluginFixturesFromModules: pytest_plugins from '${moduleFile.name}' -> ${
                        plugins.joinToString(",")
                    }"
                )
            }
            queue.addAll(plugins)
        }
    }

    private fun resolveModuleFileInRoots(
        project: com.intellij.openapi.project.Project,
        roots: Collection<VirtualFile>,
        module: String
    ): PyFile? {
        for (root in roots) {
            val resolved = resolveModuleFile(project, root, module)
            if (resolved != null) return resolved
        }
        return null
    }

    private fun extractPytestPluginModules(moduleFile: PyFile): List<String> {
        val result = mutableListOf<String>()
        moduleFile.statements.filterIsInstance<PyAssignmentStatement>().forEach { assignment ->
            val target = assignment.targets.singleOrNull() as? PyTargetExpression ?: return@forEach
            if (target.name != "pytest_plugins") return@forEach
            val value = assignment.assignedValue ?: return@forEach
            when (value) {
                is PyStringLiteralExpression -> {
                    val name = value.stringValue
                    if (name.isNotBlank()) result.add(name)
                }

                is PyListLiteralExpression -> {
                    value.elements.filterIsInstance<PyStringLiteralExpression>().forEach { element ->
                        val name = element.stringValue
                        if (name.isNotBlank()) result.add(name)
                    }
                }

                is PyTupleExpression -> {
                    value.elements.filterIsInstance<PyStringLiteralExpression>().forEach { element ->
                        val name = element.stringValue
                        if (name.isNotBlank()) result.add(name)
                    }
                }
            }
        }
        return result.distinct()
    }

    private fun resolveModuleFile(
        project: com.intellij.openapi.project.Project,
        root: VirtualFile,
        module: String
    ): PyFile? {
        val segments = module.split('.').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val pySegments = segments.dropLast(1).toMutableList()
        pySegments.add(segments.last() + ".py")
        val pyFile = VfsUtil.findRelativeFile(root, *pySegments.toTypedArray())
        val initFile = VfsUtil.findRelativeFile(root, *segments.toTypedArray(), "__init__.py")
        val target = pyFile ?: initFile ?: return null
        return PsiManager.getInstance(project).findFile(target) as? PyFile
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
                result.addAll(findNestedClassFixtureOverrides(containingClass, fixtureName))
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

    private fun findNestedClassFixtureOverrides(baseClass: PyClass, fixtureName: String): List<FixtureLink> {
        val result = mutableListOf<FixtureLink>()
        val nestedClasses = PsiTreeUtil.findChildrenOfType(baseClass, PyClass::class.java)
        for (nestedClass in nestedClasses) {
            if (nestedClass == baseClass) continue
            for (method in nestedClass.methods) {
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

        val classes = PsiTreeUtil.findChildrenOfType(file, PyClass::class.java)
        for (cls in classes) {
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
        if (DumbService.isDumb(project)) return result
        val scope = GlobalSearchScopesCore.directoryScope(project, parentDir, true)
        val candidateFiles = PytestFixtureFileIndex.findFilesWithFixture(fixtureName, project, scope)

        for (candidate in candidateFiles) {
            if (candidate == virtualFile) continue
            if (candidate.name == "conftest.py") {
                collectFixturesFromFile(conftestFile, candidate, fixtureName, result)
                continue
            }
            if (candidate.extension == "py") {
                collectFixturesFromFile(
                    conftestFile,
                    candidate,
                    fixtureName,
                    result,
                    restrictToTestFiles = true,
                    includeClassFixtures = true
                )
            }
        }

        return result
    }

    private fun collectFixturesFromFile(
        originFile: PyFile,
        virtualFile: VirtualFile,
        fixtureName: String,
        result: MutableList<FixtureLink>,
        restrictToTestFiles: Boolean = false,
        includeClassFixtures: Boolean = false
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
        collectAssignedFixtures(psiFile, fixtureName, mutableSetOf(), result)
        if (!includeClassFixtures) return
        val classes = PsiTreeUtil.findChildrenOfType(psiFile, PyClass::class.java)
        for (cls in classes) {
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
    }

    private fun collectAssignedFixtures(
        file: PyFile,
        fixtureName: String,
        seen: MutableSet<PyFunction>,
        result: MutableList<FixtureLink>,
        importElement: PsiElement? = null
    ) {
        for (statement in file.statements) {
            val assignment = statement as? PyAssignmentStatement ?: continue
            val assignedFixture = PytestFixtureUtil.getAssignedFixture(assignment) ?: continue
            if (assignedFixture.fixtureName != fixtureName) continue
            if (!seen.add(assignedFixture.fixtureFunction)) continue
            if (LOG.isDebugEnabled) {
                LOG.debug(
                    "PytestFixtureResolver.collectAssignedFixtures: file='${file.name}', fixture='${assignedFixture.fixtureName}', " +
                            "target='${assignedFixture.fixtureFunction.name}'"
                )
            }
            result.add(FixtureLink(assignedFixture.fixtureFunction, fixtureName, importElement))
        }
    }
}
