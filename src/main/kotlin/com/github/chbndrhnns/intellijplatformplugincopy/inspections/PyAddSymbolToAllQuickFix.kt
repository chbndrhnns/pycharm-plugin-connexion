package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*

/** Quick-fix that adds a symbol to __all__ in a package __init__.py.
 *
 * Behaviour:
 * - If a simple sequence __all__ assignment exists, append the missing
 *   symbol name to that sequence.
 * - If __all__ is missing entirely, create a new
 *   `__all__ = ["name"]` assignment near the top of the file.
 * - If __all__ exists but is not a sequence, the inspection does not
 *   register problems, so this fix is never invoked in that case.
 */
class PyAddSymbolToAllQuickFix(private val name: String) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String = "Add to __all__"

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val contextFile = element.containingFile ?: return
        val contextPyFile = contextFile as? PyFile ?: return

        // Decide which file's __all__ should be updated:
        // - If we are already in __init__.py, operate on this file.
        // - Otherwise, try to locate the containing package's __init__.py
        //   and update its __all__ (cross-file quick-fix behaviour).
        val (targetFile, sourceModule) = if (contextPyFile.name == PyNames.INIT_DOT_PY) {
            // We are already in the package __init__.py – operate on this file.
            contextPyFile to null
        } else {
            // We are in a regular module. Use its containingDirectory so that the
            // quick-fix operates on the *copied* test file (or the correct PSI
            // file in the project), not on the original file on disk.
            val vFile = PsiUtilCore.getVirtualFile(element) ?: return
            val parentVFile = vFile.parent ?: return
            val parentPsiDir = PsiManager.getInstance(project).findDirectory(parentVFile) ?: return
            val initFile = parentPsiDir.findFile(PyNames.INIT_DOT_PY) as? PyFile ?: return
            (updater.getWritable(initFile) as PyFile) to contextPyFile
        }

        val dunderAllAssignment = findDunderAllAssignment(targetFile)
        if (dunderAllAssignment != null) {
            val value = dunderAllAssignment.assignedValue
            if (value is PySequenceExpression) {
                updateExistingDunderAll(project, dunderAllAssignment, value)
            }
            // When invoked from a module inside a package, we must also
            // ensure that the symbol is imported from that module in the
            // package's __init__.py. The import should appear *below* the
            // __all__ assignment.
            if (sourceModule != null) {
                addOrUpdateImportForModuleSymbol(project, targetFile, dunderAllAssignment, sourceModule)
            }
            return
        }

        // No __all__ yet – create one.
        createNewDunderAll(targetFile, project)
        // If we created a new __all__ and the fix was invoked from a module,
        // we still need to add an import for the symbol from that module,
        // placing it after the freshly created __all__.
        if (sourceModule != null) {
            val newDunderAll = findDunderAllAssignment(targetFile) ?: return
            addOrUpdateImportForModuleSymbol(project, targetFile, newDunderAll, sourceModule)
        }
    }

    /**
     * Ensure there is a `from .<module> import <name>` import in [file]
     * located *after* the given [dunderAllAssignment]. If such an import
     * already exists, the name is merged into its import list.
     */
    private fun addOrUpdateImportForModuleSymbol(
        project: Project,
        file: PyFile,
        dunderAllAssignment: PyAssignmentStatement,
        sourceModule: PyFile,
    ) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(file)

        val moduleName = sourceModule.name.removeSuffix(".py")

        // All names currently exported via __all__; we will prefer to import
        // *all* of them from the module when synthesising a fresh import
        // statement so that the import list stays in sync with __all__.
        val exportedNames: List<String> = (dunderAllAssignment.assignedValue as? PySequenceExpression)
            ?.elements
            ?.mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
            ?.distinct()
            ?: listOf(name)

        // Try to find an existing `from .<module> import ...` statement.
        val existingImport = file.statements
            .filterIsInstance<PyFromImportStatement>()
            .firstOrNull { fromImport ->
                val source = fromImport.importSource
                // We want "from .module import ..." – a relative import
                // whose referenced name matches the module name.
                (fromImport.relativeLevel ?: 0) > 0 && source?.referencedName == moduleName
            }

        if (existingImport != null) {
            // Merge the symbol into the existing import list if not already
            // present.
            val importedNames = existingImport.importElements
                .mapNotNull { it.importedQName?.lastComponent }

            if (!importedNames.contains(name)) {
                val newImport = generator.createFromText(
                    languageLevel,
                    PyFromImportStatement::class.java,
                    "from .$moduleName import $name",
                )

                val newElement = newImport.importElements.firstOrNull()
                if (newElement != null) {
                    existingImport.add(newElement)
                }
            }
            return
        }

        // No suitable import found – create a new one and insert it after
        // the __all__ assignment. We use a relative import so that it works
        // both in real projects and test data.
        val importNames = (exportedNames + name).distinct().sorted()
        val importStatement = generator.createFromText(
            languageLevel,
            PyFromImportStatement::class.java,
            "from .$moduleName import ${importNames.joinToString(", ")}",
        )

        file.addAfter(importStatement, dunderAllAssignment)
    }

    private fun createNewDunderAll(file: PyFile, project: Project) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(file)

        val assignment = generator.createFromText(
            languageLevel,
            PyAssignmentStatement::class.java,
            "__all__ = ['$name']",
        )

        val anchor = file.statements.firstOrNull()
        if (anchor != null) {
            file.addBefore(assignment, anchor)
        } else {
            file.add(assignment)
        }
    }

    /**
     * Appends [name] to an existing simple sequence __all__ assignment using
     * `insertItemIntoListRemoveRedundantCommas`, but only when the name is
     * not already present.
     */
    private fun updateExistingDunderAll(
        project: Project,
        assignment: PyAssignmentStatement,
        sequence: PySequenceExpression,
    ) {
        val generator = PyElementGenerator.getInstance(project)

        val existingNames = sequence.elements.mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
        if (existingNames.contains(name)) {
            return
        }

        // Use the same quoting style as our other __all__ helpers, i.e. a
        // simple single-quoted string literal. Relying on the default
        // createStringLiteralFromString() may flip to double quotes and break
        // text-based test expectations.
        val languageLevel = LanguageLevel.forElement(sequence)
        val stringLiteral = generator.createExpressionFromText(
            languageLevel,
            "'$name'",
        ) as PyStringLiteralExpression
        val firstElement = sequence.elements.firstOrNull()
        generator.insertItemIntoListRemoveRedundantCommas(
            sequence,
            firstElement,
            stringLiteral,
        )
    }

    private fun findDunderAllAssignment(file: PyFile): PyAssignmentStatement? {
        for (statement in file.statements) {
            val assignment = statement as? PyAssignmentStatement ?: continue
            for (target in assignment.targets) {
                if (PyNames.ALL == target.name) {
                    return assignment
                }
            }
        }
        return null
    }
}
