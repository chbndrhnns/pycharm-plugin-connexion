package com.github.chbndrhnns.betterpy.features.intentions.customtype

import com.github.chbndrhnns.betterpy.core.psi.PyImportService
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTypedElement

/**
 * Thin wrapper around [PyImportService] for the "Introduce custom type from
 * stdlib" feature.
 *
 * The goal is to keep the intention depending on a small, feature‑local
 * abstraction instead of the more general utility service, while preserving
 * existing behaviour.
 */
class ImportManager(
    private val delegate: PyImportService = PyImportService(),
) {

    /**
     * Ensure [insertedClass] is importable in [usageFile]'s scope.
     *
     * This simply delegates to [PyImportService.ensureImportedIfNeeded] and
     * keeps the same semantics regarding builtins, existing imports, and
     * relative import reuse.
     */
    fun ensureImportedIfNeeded(
        usageFile: PyFile,
        anchor: PyTypedElement,
        insertedClass: PsiNamedElement?,
    ) {
        delegate.ensureImportedIfNeeded(usageFile as PsiFile, anchor, insertedClass)
    }
}
