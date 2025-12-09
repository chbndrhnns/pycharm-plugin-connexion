package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.roots.ProjectRootManager
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
        if (!PythonVersionGuard.isSatisfied(holder.project)) {
            return object : PyElementVisitor() {}
        }
        val settings = PluginSettingsState.instance().state
        if (!settings.enablePrivateModuleImportInspection) {
            return object : PyElementVisitor() {}
        }

        return object : PyElementVisitor() {
            override fun visitPyFile(node: PyFile) {
                super.visitPyFile(node)

                val vFile = node.virtualFile ?: return
                val index = ProjectRootManager.getInstance(node.project).fileIndex
                val isUserSource = index.isInSourceContent(vFile) &&
                        !index.isInLibraryClasses(vFile) &&
                        !index.isInLibrarySource(vFile)
                if (!isUserSource) return

                for (statement in node.fromImports) {
                    val fromImport = statement ?: continue
                    checkFromImport(node, fromImport, holder)
                }
            }
        }
    }

    private fun checkFromImport(file: PyFile, fromImport: PyFromImportStatement, holder: ProblemsHolder) {
        if (fromImport.isStarImport) return

        val resolved = fromImport.resolveImportSource() as? PyFile ?: return

        // Only care about private modules like _lib.py
        if (!resolved.name.startsWith("_")) return

        val directory = resolved.containingDirectory ?: return
        val packageInit = directory.findFile(PyNames.INIT_DOT_PY) as? PyFile ?: return

        // Do not offer this quick-fix inside the package __init__.py that
        // performs the re-export itself. In that file we *want* the import
        // from the private module, not from the public package.
        if (file == packageInit) return

        // Do not offer this quick-fix if the importing file is in the same
        // package as the __init__.py that exports the symbol. Files within
        // the same package should be allowed to import from private modules
        // without being forced to use the public export.
        if (file.containingDirectory == directory) return

        val dunderAllNames = findDunderAllNames(packageInit) ?: return

        for (importElement in fromImport.importElements) {
            val name = importElement.importedQName?.lastComponent ?: continue

            if (dunderAllNames.contains(name)) {
                // Anchor the quick-fix on the whole import statement so it is
                // available when the caret is anywhere on the statement, not
                // just on the imported name.
                holder.registerProblem(
                    fromImport,
                    "Symbol '$name' is exported from package __all__; import it from the package instead of the private module",
                    PyUseExportedSymbolFromPackageQuickFix(name),
                )
            } else {
                // For the "make public" quick-fix we keep the anchor on the
                // individual imported element, as the change is specific to
                // that symbol.
                holder.registerProblem(
                    importElement,
                    "Symbol '$name' is not exported from package __all__ yet; make it public and import from the package",
                    PyMakeSymbolPublicAndUseExportedSymbolQuickFix(name),
                )
            }
        }
    }

    private fun findDunderAllNames(file: PyFile): Collection<String>? {
        val dunderAllAssignment = (file.findTopLevelAttribute(PyNames.ALL)?.parent as? PyAssignmentStatement)
            ?: return emptyList()

        return when (val value = dunderAllAssignment.assignedValue) {
            null -> emptyList()
            is PySequenceExpression -> value.elements
                .mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }

            else -> null
        }
    }
}
