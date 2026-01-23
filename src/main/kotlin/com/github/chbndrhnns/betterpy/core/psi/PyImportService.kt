package com.github.chbndrhnns.betterpy.core.psi

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Small service responsible for Python import detection/addition.
 *
 * This is a direct extraction of the import helpers previously embedded in
 * WrapWithExpectedTypeIntention, with a slightly generalized entrypoint that
 * accepts the resolved symbol (when available).
 */
class PyImportService {
    /**
     * Ensure a module-level import (e.g. ``import pytest``) exists in [file].
     */
    fun ensureModuleImported(
        file: PyFile,
        moduleName: String,
        priority: AddImportHelper.ImportPriority = AddImportHelper.ImportPriority.THIRD_PARTY,
    ) {
        if (isImported(file, moduleName)) return

        AddImportHelper.addImportStatement(
            file,
            moduleName,
            null,
            priority,
            null
        )
    }

    /**
     * Ensure a `from <module> import <name>` import exists in [file].
     */
    fun ensureFromImport(file: PyFile, moduleName: String, name: String) {
        val existingFromImport = file.statements
            .filterIsInstance<PyFromImportStatement>()
            .firstOrNull { it.importSourceQName?.toString() == moduleName }

        if (existingFromImport != null) {
            if (existingFromImport.importElements.any { it.importedQName?.lastComponent == name }) return

            val generator = PyElementGenerator.getInstance(file.project)
            val languageLevel = LanguageLevel.forElement(file)
            val newImport = generator.createFromText(
                languageLevel,
                PyFromImportStatement::class.java,
                "from $moduleName import $name",
            )
            val newElement = newImport.importElements.firstOrNull() ?: return
            val last = existingFromImport.importElements.lastOrNull()
            if (last != null) {
                existingFromImport.addAfter(newElement, last)
            } else {
                existingFromImport.add(newElement)
            }
            return
        }

        val generator = PyElementGenerator.getInstance(file.project)
        val languageLevel = LanguageLevel.forElement(file)
        val newImport = generator.createFromText(
            languageLevel,
            PyFromImportStatement::class.java,
            "from $moduleName import $name",
        )

        val imports = file.importBlock
        if (imports.isNotEmpty()) {
            val lastImport = imports.last()
            file.addAfter(newImport, lastImport)
            file.addAfter(PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("\n"), lastImport)
        } else {
            val firstStatement = file.statements.firstOrNull()
            if (firstStatement != null) {
                file.addBefore(newImport, firstStatement)
            } else {
                file.add(newImport)
            }
        }
    }

    /**
     * Ensure [element] is importable in [file]'s scope. Skips builtins and existing imports.
     *
     * @param file the file to add the import into
     * @param anchor an anchor PSI element used by AddImportHelper
     * @param element a resolved PSI symbol for the expected type (may be null)
     */
    fun ensureImportedIfNeeded(file: PsiFile, anchor: PyTypedElement, element: PsiNamedElement?) {
        element ?: return

        // Don't import builtins
        if (PyBuiltinCache.getInstance(anchor).isBuiltin(element)) return

        val name = element.name ?: return

        // If the symbol is already available in the current scope (module/locals/imports/builtins), do not import
        val owner = ScopeUtil.getScopeOwner(anchor) ?: (file as? PyFile)
        if (owner != null) {
            val tec = TypeEvalContext.codeAnalysis(file.project, file)
            val resolved = PyResolveUtil.resolveQualifiedNameInScope(
                QualifiedName.fromDottedString(name), owner, tec
            )
            if (resolved.isNotEmpty()) return
        }

        // Check if already imported using any import style (absolute and relative)
        if (isImported(file, name)) return

        val pyFile = file as? PyFile

        // If there is already a "from ... import ..." that targets the same
        // module as [element] (including relative imports such as
        // ``from .model import D``), prefer to *reuse* that import statement
        // by appending the new name instead of introducing a new absolute
        // import. This preserves relative imports as used at the call site.
        if (pyFile != null) {
            val elementFile = element.containingFile

            val existingFromImport = pyFile.importBlock
                .filterIsInstance<PyFromImportStatement>()
                .firstOrNull { fromStmt ->
                    // We consider a "from" import suitable if at least one of
                    // its imported names resolves to the same file as the
                    // element we want to import.
                    fromStmt.importElements.any { imported ->
                        imported.multiResolve()
                            .mapNotNull { it.element as? PsiNamedElement }
                            .any { it.containingFile == elementFile }
                    }
                }

            if (existingFromImport != null) {
                // If the name is already present, we don't need to modify the
                // import at all.
                if (existingFromImport.importElements.any { it.visibleName == name }) return

                // Reconstruct the "from" import from its PSI parts instead
                // of concatenating the whole statement text. This lets us
                // preserve the written module reference, including any
                // relative level (e.g. leading dots like ".model"), while
                // still building the updated statement in a structured way.
                val relativeLevel = existingFromImport.relativeLevel ?: 0
                val importSourceText = existingFromImport.importSource?.text

                // PyFromImportStatement represents relative imports using a
                // separate relativeLevel (number of leading dots) plus the
                // importSource text without those dots. Reconstruct the full
                // module part as it should appear in the statement.
                val modulePart = when {
                    importSourceText != null && relativeLevel > 0 ->
                        ".".repeat(relativeLevel) + importSourceText

                    importSourceText != null ->
                        importSourceText

                    relativeLevel > 0 ->
                        ".".repeat(relativeLevel)

                    else -> return
                }
                val existingNames = existingFromImport.importElements
                    .mapNotNull { it.visibleName }
                    .joinToString(", ")

                val newImportText = if (existingNames.isNotEmpty()) {
                    "from $modulePart import $existingNames, $name"
                } else {
                    "from $modulePart import $name"
                }

                val generator = PyElementGenerator.getInstance(file.project)
                val newFromImport = generator.createFromText(
                    LanguageLevel.getLatest(),
                    PyFromImportStatement::class.java,
                    newImportText,
                )

                existingFromImport.replace(newFromImport)
                return
            }
        }

        // Fall back to the platform's general helper, which will decide
        // between module vs "from" import according to user settings.
        AddImportHelper.addImport(element, file, anchor)
    }

    /** Returns true if [name] is already imported in [file] using any supported style. */
    fun isImported(file: PsiFile, name: String): Boolean {
        val pyFile = file as? PyFile ?: return false
        for (import in pyFile.importBlock) {
            when (import) {
                is PyFromImportStatement -> {
                    if (import.importElements.any { it.importedQName?.lastComponent == name }) return true
                }

                is PyImportStatement -> {
                    if (import.importElements.any { it.visibleName == name }) return true
                }
            }
        }
        return false
    }
}
