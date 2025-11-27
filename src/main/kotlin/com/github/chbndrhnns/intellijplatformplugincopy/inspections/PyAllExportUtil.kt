package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiParserFacade
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*

/** Utility responsible for keeping a package's {@code __all__} and re-export
 * imports in sync when a symbol should be made public.
 *
 * Extracted from {@link PyAddSymbolToAllQuickFix} so that other quick-fixes
 * (e.g. the combined "make symbol public and import from package" fix) can
 * reuse the same logic.
 */
object PyAllExportUtil {

    /**
     * Ensure that [name] is present in [targetFile]'s simple sequence
     * {@code __all__} assignment, creating one if needed. When
     * [sourceModule] is not null, also ensure that there is a relative
     * re-export import for [name] from that module, located after the
     * {@code __all__} assignment.
     */
    fun ensureSymbolExported(
        project: Project,
        targetFile: PyFile,
        name: String,
        sourceModule: PyFile?,
    ) {
        val dunderAllAssignment = findDunderAllAssignment(targetFile, name)
        if (dunderAllAssignment != null) {
            val value = dunderAllAssignment.assignedValue
            if (value is PySequenceExpression) {
                updateExistingDunderAll(project, dunderAllAssignment, value, name)
            }
            if (sourceModule != null) {
                addOrUpdateImportForModuleSymbol(project, targetFile, dunderAllAssignment, sourceModule, name)
            }
            return
        }

        // No __all__ yet – create one.
        createNewDunderAll(targetFile, project, name)
        // If we created a new __all__ and the fix was invoked from a module,
        // we still need to add an import for the symbol from that module,
        // placing it after the freshly created __all__.
        if (sourceModule != null) {
            val newDunderAll = findDunderAllAssignment(targetFile, name) ?: return
            addOrUpdateImportForModuleSymbol(project, targetFile, newDunderAll, sourceModule, name)
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
        name: String,
    ) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(file)

        val moduleName = sourceModule.name.removeSuffix(".py")

        // All names currently exported via __all__; may be useful when there
        // are no existing re-export imports yet and this module is the first
        // one being wired up.
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
        //
        // IMPORTANT:
        // - When there are already other from-imports in this __init__.py,
        //   we must assume that __all__ aggregates symbols from multiple
        //   modules. In that case we conservatively import [name] only from
        //   [sourceModule] to avoid pulling unrelated symbols from the wrong
        //   module.
        // - When there are no existing from-imports, this module is likely
        //   the first implementation module being wired up. In that simple
        //   case it's safe (and preserves the previous behaviour expected by
        //   older tests) to create a combined import that mirrors all names
        //   currently present in __all__.
        val hasAnyFromImports = file.statements.any { it is PyFromImportStatement }
        val importNames = if (hasAnyFromImports) {
            listOf(name)
        } else {
            (exportedNames + name).distinct().sorted()
        }
        val importStatement = generator.createFromText(
            languageLevel,
            PyFromImportStatement::class.java,
            "from .$moduleName import ${importNames.joinToString(", ")}",
        )

        file.addAfter(importStatement, dunderAllAssignment)
    }

    private fun createNewDunderAll(file: PyFile, project: Project, name: String) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(file)

        val assignment = generator.createFromText(
            languageLevel,
            PyAssignmentStatement::class.java,
            "__all__ = ['$name']",
        )

        // If the file has a module-level docstring, keep it at the very top
        // and insert __all__ *after* it with exactly one blank line in
        // between, so the layout becomes:
        //
        //   """docstring"""
        //
        //   __all__ = [...]
        val docstringExpr = file.docStringExpression
        if (docstringExpr != null) {
            val parserFacade = PsiParserFacade.SERVICE.getInstance(project)
            val whitespace = parserFacade.createWhiteSpaceFromText("\n\n")
            val docstringOwner = (docstringExpr.parent.takeIf { it.parent == file } ?: docstringExpr)
            val wsElement = file.addAfter(whitespace, docstringOwner)
            file.addAfter(assignment, wsElement)
            return
        }

        // No module-level docstring – fall back to the original behaviour of
        // inserting __all__ before the first top-level statement (or as the
        // only statement in an otherwise empty file).
        val statements = file.statements
        val firstStatement = statements.firstOrNull()

        if (firstStatement == null) {
            file.add(assignment)
        } else {
            file.addBefore(assignment, firstStatement)
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
        name: String,
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

    private fun findDunderAllAssignment(file: PyFile, name: String): PyAssignmentStatement? {
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