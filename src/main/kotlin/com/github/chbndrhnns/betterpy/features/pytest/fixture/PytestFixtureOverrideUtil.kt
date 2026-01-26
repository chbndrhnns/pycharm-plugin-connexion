package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Collects pytest fixtures that can be overridden in the current scope.
 */
object PytestFixtureOverrideUtil {
    private val LOG = Logger.getInstance(PytestFixtureOverrideUtil::class.java)

    fun collectOverridableFixturesInClass(
        targetClass: PyClass,
        file: PyFile,
        context: TypeEvalContext
    ): List<FixtureLink> {
        if (!PytestNaming.isTestFile(file) && file.name != "conftest.py") {
            if (LOG.isDebugEnabled) {
                LOG.debug("PytestFixtureOverrideUtil.collectOverridableFixturesInClass: skipping non-test file '${file.name}'")
            }
            return emptyList()
        }
        val result = collectForClass(targetClass, file, context)
        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureOverrideUtil.collectOverridableFixturesInClass: found ${result.size} fixture(s)")
        }
        return result
    }

    fun collectOverridableFixtures(
        element: PsiElement,
        context: TypeEvalContext
    ): List<FixtureLink> {
        val file = element.containingFile as? PyFile ?: return emptyList()
        if (!PytestNaming.isTestFile(file) && file.name != "conftest.py") {
            if (LOG.isDebugEnabled) {
                LOG.debug("PytestFixtureOverrideUtil.collectOverridableFixtures: skipping non-test file '${file.name}'")
            }
            return emptyList()
        }

        val targetClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
        val result = if (targetClass != null) {
            collectForClass(targetClass, file, context)
        } else {
            collectForModule(file, context)
        }
        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureOverrideUtil.collectOverridableFixtures: found ${result.size} fixture(s)")
        }
        return result
    }

    private fun collectForClass(
        targetClass: PyClass,
        file: PyFile,
        context: TypeEvalContext
    ): List<FixtureLink> {
        val existingNames = collectFixtureNamesInClass(targetClass)
        val result = LinkedHashMap<String, FixtureLink>()

        // Base class fixtures first (closest ancestors first).
        val ancestors = LinkedHashSet<PyClass>()
        for (superExpr in targetClass.superClassExpressions) {
            val resolved = superExpr.reference?.resolve() as? PyClass
            if (resolved != null) {
                ancestors.add(resolved)
            }
        }
        val baseNames = targetClass.superClassExpressions.mapNotNull { it.text?.trim() }.toSet()
        if (baseNames.isNotEmpty()) {
            file.topLevelClasses
                .filter { it.name in baseNames }
                .forEach { ancestors.add(it) }
        }
        ancestors.addAll(targetClass.getAncestorClasses(context))
        ancestors.addAll(targetClass.getAncestorClasses(null))
        for (ancestor in ancestors) {
            addFixturesFromClass(ancestor, existingNames, result)
        }

        // Outer class fixtures (for nested class scopes).
        var outer = PsiTreeUtil.getParentOfType(targetClass, PyClass::class.java, true)
        while (outer != null) {
            addFixturesFromClass(outer, existingNames, result)
            outer = PsiTreeUtil.getParentOfType(outer, PyClass::class.java, true)
        }

        // Then module-level fixtures.
        addFixturesFromModule(file, existingNames, result)

        // Then imported fixtures.
        addImportedFixtures(file, existingNames, result)

        // Finally conftest fixtures (nearest first).
        addConftestFixtures(file, existingNames, result)

        return result.values.toList()
    }

    private fun collectForModule(file: PyFile, context: TypeEvalContext): List<FixtureLink> {
        val existingNames = collectFixtureNamesInModule(file)
        val result = LinkedHashMap<String, FixtureLink>()

        addImportedFixtures(file, existingNames, result)
        addConftestFixtures(file, existingNames, result)

        return result.values.toList()
    }

    private fun collectFixtureNamesInClass(pyClass: PyClass): Set<String> {
        val names = mutableSetOf<String>()
        for (method in pyClass.methods) {
            if (!PytestFixtureUtil.isFixtureFunction(method)) continue
            val name = PytestFixtureUtil.getFixtureName(method) ?: continue
            names.add(name)
        }
        return names
    }

    private fun collectFixtureNamesInModule(file: PyFile): Set<String> {
        val names = mutableSetOf<String>()
        for (function in file.topLevelFunctions) {
            if (!PytestFixtureUtil.isFixtureFunction(function)) continue
            val name = PytestFixtureUtil.getFixtureName(function) ?: continue
            names.add(name)
        }
        return names
    }

    private fun addFixturesFromClass(
        pyClass: PyClass,
        existingNames: Set<String>,
        result: LinkedHashMap<String, FixtureLink>
    ) {
        for (method in pyClass.methods) {
            if (!PytestFixtureUtil.isFixtureFunction(method)) continue
            val name = PytestFixtureUtil.getFixtureName(method) ?: continue
            if (name in existingNames || result.containsKey(name)) continue
            result[name] = FixtureLink(method, name)
        }
    }

    private fun addFixturesFromModule(
        file: PyFile,
        existingNames: Set<String>,
        result: LinkedHashMap<String, FixtureLink>
    ) {
        for (function in file.topLevelFunctions) {
            if (!PytestFixtureUtil.isFixtureFunction(function)) continue
            val name = PytestFixtureUtil.getFixtureName(function) ?: continue
            if (name in existingNames || result.containsKey(name)) continue
            result[name] = FixtureLink(function, name)
        }
    }

    private fun addImportedFixtures(
        file: PyFile,
        existingNames: Set<String>,
        result: LinkedHashMap<String, FixtureLink>
    ) {
        for (importElement in file.importTargets) {
            val resolved = importElement.reference?.resolve()
            if (resolved is PyFunction && PytestFixtureUtil.isFixtureFunction(resolved)) {
                val name = PytestFixtureUtil.getFixtureName(resolved) ?: continue
                if (name in existingNames || result.containsKey(name)) continue
                result[name] = FixtureLink(resolved, name, importElement)
            }
        }

        for (importStatement in PsiTreeUtil.findChildrenOfType(file, PyFromImportStatement::class.java)) {
            if (!importStatement.isStarImport) continue
            val importSource = importStatement.importSource
            val resolvedModule = importSource?.reference?.resolve()
            if (resolvedModule !is PyFile) continue
            for (function in resolvedModule.topLevelFunctions) {
                if (!PytestFixtureUtil.isFixtureFunction(function)) continue
                val name = PytestFixtureUtil.getFixtureName(function) ?: continue
                if (name in existingNames || result.containsKey(name)) continue
                result[name] = FixtureLink(function, name, importStatement)
            }
        }
    }

    private fun addConftestFixtures(
        file: PyFile,
        existingNames: Set<String>,
        result: LinkedHashMap<String, FixtureLink>
    ) {
        val virtualFile = file.virtualFile ?: file.containingDirectory?.virtualFile ?: return
        var currentDir = virtualFile.parent

        while (currentDir != null) {
            val conftestFile = currentDir.findChild("conftest.py")
            if (conftestFile != null && !conftestFile.isDirectory) {
                val psiFile = file.manager.findFile(conftestFile) as? PyFile
                if (psiFile != null) {
                    for (function in psiFile.topLevelFunctions) {
                        if (!PytestFixtureUtil.isFixtureFunction(function)) continue
                        val name = PytestFixtureUtil.getFixtureName(function) ?: continue
                        if (name in existingNames || result.containsKey(name)) continue
                        result[name] = FixtureLink(function, name)
                    }
                }
            }
            currentDir = currentDir.parent
        }
    }
}