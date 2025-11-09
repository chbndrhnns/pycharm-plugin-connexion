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

        // Use the platform's import helper to add the import
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
