package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyTypedElement
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

                val generator = com.jetbrains.python.psi.PyElementGenerator.getInstance(file.project)
                val newFromImport = generator.createFromText(
                    com.jetbrains.python.psi.LanguageLevel.getLatest(),
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
