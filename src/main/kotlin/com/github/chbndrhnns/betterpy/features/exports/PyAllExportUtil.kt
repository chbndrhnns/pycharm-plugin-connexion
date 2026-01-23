package com.github.chbndrhnns.betterpy.features.exports

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.documentation.docstrings.DocStringUtil
import com.jetbrains.python.psi.*

internal fun findDunderAllAssignment(file: PyFile): PyAssignmentStatement? {
    val target = file.findTopLevelAttribute(PyNames.ALL)
    return target?.parent as? PyAssignmentStatement
}

internal fun extractDunderAllNames(assignment: PyAssignmentStatement): Set<String>? {
    return when (val value = assignment.assignedValue) {
        null -> emptySet()
        is PySequenceExpression -> value.elements
            .mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
            .toSet()

        else -> null
    }
}

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
        val dunderAllAssignment = findDunderAllAssignment(targetFile)
        if (dunderAllAssignment != null) {
            val value = dunderAllAssignment.assignedValue
            if (value is PySequenceExpression) {
                updateExistingDunderAll(project, value, name)
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
            val newDunderAll = findDunderAllAssignment(targetFile) ?: return
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

        val targetDir = file.containingDirectory?.virtualFile ?: file.virtualFile?.parent
        val sourceFile = sourceModule.virtualFile
        val relativePath = if (targetDir != null && sourceFile != null) {
            VfsUtilCore.getRelativePath(sourceFile, targetDir)
        } else {
            null
        }
        val moduleName = relativePath?.removeSuffix(".py")?.replace('/', '.') ?: sourceModule.name.removeSuffix(".py")

        // Try to find an existing `from .<module> import ...` statement.
        val existingImport = findRelativeImport(file, moduleName)

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

        // All names currently exported via __all__; may be useful when there
        // are no existing re-export imports yet and this module is the first
        // one being wired up.
        val exportedNames: List<String> = (dunderAllAssignment.assignedValue as? PySequenceExpression)
            ?.let { getExportedNames(it) }
            ?: listOf(name)

        val importNames = resolveImportNamesForNewStatement(file, exportedNames, name)

        val importStatement = generator.createFromText(
            languageLevel,
            PyFromImportStatement::class.java,
            "from .$moduleName import ${importNames.joinToString(", ")}",
        )

        file.addAfter(importStatement, dunderAllAssignment)
    }

    private fun resolveImportNamesForNewStatement(
        targetFile: PyFile,
        exportedNames: List<String>,
        nameToExport: String
    ): List<String> {
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
        val hasAnyFromImports = targetFile.statements.any { it is PyFromImportStatement }

        // Case 1: Existing imports imply complex structure -> conservative import
        if (hasAnyFromImports) {
            return listOf(nameToExport)
        }

        // Case 2: No imports -> likely first wiring -> mirror all exported names
        // BUT: Do not import names that are already defined in the file itself.
        val definedNames = (targetFile.topLevelClasses.mapNotNull { it.name } +
                targetFile.topLevelFunctions.mapNotNull { it.name } +
                targetFile.topLevelAttributes.mapNotNull { it.name }).toSet()

        return (exportedNames + nameToExport)
            .filter { it !in definedNames }
            .distinct()
            .sorted()
    }

    private fun findRelativeImport(file: PyFile, moduleName: String): PyFromImportStatement? {
        return file.statements
            .filterIsInstance<PyFromImportStatement>()
            .firstOrNull { fromImport ->
                val source = fromImport.importSource
                // We want "from .module import ..." – a relative import
                // whose referenced name matches the module name.
                (fromImport.relativeLevel ?: 0) > 0 && source?.referencedName == moduleName
            }
    }

    private fun createNewDunderAll(file: PyFile, project: Project, name: String) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(file)

        val lit = generator.createStringLiteralFromString(name, true)
        val stmtText = "__all__ = [${lit.text}]"
        val assignment = generator.createFromText(
            languageLevel,
            PyAssignmentStatement::class.java,
            stmtText,
        )

        insertStatementBelowDocstring(project, file, assignment)
        reformat(project, file, assignment)
    }

    private fun reformat(project: Project, file: PyFile, element: PyElement) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        if (document != null) {
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        }
        CodeStyleManager.getInstance(project).reformat(element)
    }

    private fun insertStatementBelowDocstring(
        project: Project,
        file: PyFile,
        allStatementText: PyStatement, // e.g. "__all__ = ['a', 'b']"
    ) {
        // 1) Find module docstring (expression) and convert to its statement
        val docExpr = DocStringUtil.findDocStringExpression(file)
        val docStmt = PsiTreeUtil.getParentOfType(docExpr, PyStatement::class.java, /* strict = */ false)
        val inserted = when {
            docStmt != null -> file.addAfter(allStatementText, docStmt) as PyStatement
            file.statements.isNotEmpty() -> file.addBefore(allStatementText, file.statements.first()) as PyStatement
            else -> file.add(allStatementText) as PyStatement
        }

        // 2) Normalize spacing to exactly one blank line after the docstring
        if (docStmt != null) {
            val afterDoc = docStmt.nextSibling
            val parser = PsiParserFacade.getInstance(project)
            val oneBlank = parser.createWhiteSpaceFromText("\n\n")
            when (afterDoc) {
                is PsiWhiteSpace -> afterDoc.replace(oneBlank)
                else -> file.addAfter(oneBlank, docStmt)
            }
        }

        // 3) Let code style polish the result
        reformat(project, file, inserted)
    }

    /**
     * Appends [name] to an existing simple sequence __all__ assignment using
     * `insertItemIntoListRemoveRedundantCommas`, but only when the name is
     * not already present.
     */
    private fun updateExistingDunderAll(
        project: Project,
        sequence: PySequenceExpression,
        name: String,
    ) {
        val file = sequence.containingFile as? PyFile ?: return
        val generator = PyElementGenerator.getInstance(project)

        val existingNames = getExportedNames(sequence)
        if (name in existingNames) return

        val firstElement = sequence.elements.firstOrNull()

        val newItem: PyExpression = generator.createStringLiteralFromString(name, false)

        generator.insertItemIntoListRemoveRedundantCommas(
            sequence,
            firstElement,
            newItem,
        )
        reformat(project, file, sequence)
    }

    private fun getExportedNames(sequence: PySequenceExpression): List<String> {
        return sequence.elements
            .mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
    }

}
