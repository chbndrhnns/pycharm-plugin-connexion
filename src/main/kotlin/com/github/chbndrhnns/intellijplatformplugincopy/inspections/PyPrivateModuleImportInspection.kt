package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*

/**
 * Inspection that suggests importing symbols from the public package export
 * instead of a private implementation module when they are already exported
 * via ``__all__``.
 *
 * Example:
 *
 *     # mypackage/__init__.py
 *     __all__ = ["Client"]
 *     from ._lib import Client
 *
 *     # cli.py
 *     from .mypackage._lib import Client
 *
 * At the import in ``cli.py`` the inspection will offer a quick-fix to
 * rewrite the import as ``from .mypackage import Client``.
 */
class PyPrivateModuleImportInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor {
        val settings = PluginSettingsState.instance().state
        if (!settings.enablePyMissingInDunderAllInspection) {
            // Reuse the same setting gate as PyMissingInDunderAllInspection so
            // users can disable all __all__/export related checks at once.
            return object : PyElementVisitor() {}
        }

        return object : PyElementVisitor() {
            override fun visitPyFile(node: PyFile) {
                super.visitPyFile(node)

                for (statement in node.statements) {
                    val fromImport = statement as? PyFromImportStatement ?: continue
                    checkFromImport(fromImport, holder)
                }
            }
        }
    }

    private fun checkFromImport(fromImport: PyFromImportStatement, holder: ProblemsHolder) {
        if (fromImport.isStarImport) return

        val resolved = fromImport.resolveImportSource() as? PyFile ?: return

        // Only care about private modules like _lib.py
        if (!resolved.name.startsWith("_")) return

        val directory = resolved.containingDirectory ?: return
        val packageInit = directory.findFile(PyNames.INIT_DOT_PY) as? PyFile ?: return

        val dunderAllNames = findDunderAllNames(packageInit) ?: return
        if (dunderAllNames.isEmpty()) return

        for (importElement in fromImport.importElements) {
            val name = importElement.importedQName?.lastComponent ?: continue

            if (!dunderAllNames.contains(name)) continue

            registerProblemForImportElement(importElement, name, holder)
        }
    }

    private fun registerProblemForImportElement(
        importElement: PyImportElement,
        name: String,
        holder: ProblemsHolder,
    ) {
        holder.registerProblem(
            importElement,
            "Symbol '$name' is exported from package __all__; import it from the package instead of the private module",
            PyUseExportedSymbolFromPackageQuickFix(name),
        )
    }

    private fun findDunderAllNames(file: PyFile): Collection<String>? {
        val dunderAllAssignment = file.statements
            .asSequence()
            .mapNotNull { it as? PyAssignmentStatement }
            .firstOrNull { assignment ->
                assignment.targets.any { target -> target.name == PyNames.ALL }
            }
            ?: return emptyList()

        return when (val value = dunderAllAssignment.assignedValue) {
            null -> emptyList()
            is PySequenceExpression -> value.elements
                .mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }

            else -> null
        }
    }
}
