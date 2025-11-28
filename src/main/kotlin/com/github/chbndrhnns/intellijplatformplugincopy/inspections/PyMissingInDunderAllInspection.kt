package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.jetbrains.python.PyNames
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*

/**
 * Inspection that ensures public symbols are exported via a package's
 * {@code __all__}.
 *
 * Behaviour:
 * - For package {@code __init__.py} itself, checks that all public symbols
 *   defined in the file are present in its own {@code __all__} sequence.
 * - For regular modules that belong to a package, checks that public symbols
 *   are exported from the containing package's {@code __init__.py} (if it has
 *   a simple sequence {@code __all__}).
 * - Skips files where {@code __all__} exists but is not assigned a simple
 *   sequence (list/tuple); in such cases we do not try to infer or modify
 *   exports.
 * - When {@code __all__} is missing entirely, problems are still reported and
 *   the quick-fix will create a new {@code __all__} assignment when operating
 *   on {@code __init__.py}.
 */
class PyMissingInDunderAllInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor {
        val settings = PluginSettingsState.instance().state
        if (!settings.enablePyMissingInDunderAllInspection) {
            // Return a no-op visitor when the inspection is disabled in settings.
            return object : PyElementVisitor() {}
        }

        return Visitor(holder, session)
    }

    private class Visitor(
        private val holder: ProblemsHolder,
        private val session: LocalInspectionToolSession,
    ) : PyElementVisitor() {

        /**
         * Hardcoded allowlists for symbols that should not be required to
         * appear in __all__.
         *
         * First step: keep it simple and prefix‑based so that we can later
         * move this into configurable settings if needed.
         */
        private val allowlistedModuleNamePrefixes = listOf(
            "test_",
            "tests_",
        )

        private val allowlistedExactModuleNames = setOf(
            "tests",
        )

        private val allowlistedFunctionNamePrefixes = listOf(
            // Common test helpers / pytest style tests
            "test_",
        )

        private val allowlistedClassNamePrefixes = listOf(
            // Typical unittest / pytest style test classes
            "Test",
        )

        override fun visitPyFile(node: PyFile) {
            super.visitPyFile(node)

            // Skip stdlib and third‑party library code – only inspect project content.
            if (!isUserCodeFile(node)) return

            val directory = node.containingDirectory

            if (node.name == PyNames.INIT_DOT_PY) {
                checkInitFileExports(node)
            } else if (directory != null && directory.findFile(PyNames.INIT_DOT_PY) is PyFile) {
                // Only run the cross-file package export check for modules
                // that actually belong to a package (i.e. live next to an
                // __init__.py).
                checkModuleExportsFromContainingPackage(node)
            }
        }

        private fun isUserCodeFile(file: PyFile): Boolean {
            val vFile = file.virtualFile ?: return true
            val project = file.project
            val index = ProjectRootManager.getInstance(project).fileIndex

            // Only inspect files that are part of the project's own sources.
            // This skips stdlib, installed packages and other library roots.
            return index.isInContent(vFile)
        }

        /**
         * Original behaviour: ensure symbols defined in __init__.py are
         * exported via that file's own __all__.
         */
        private fun checkInitFileExports(initFile: PyFile) {
            if (isAllowlistedModule(initFile)) return
            // For __init__.py itself we want slightly different semantics
            // than for regular modules:
            // - If there is *no* __all__ assignment, we still report
            //   problems and let the quick-fix create __all__ from
            //   scratch.
            // - If __all__ exists but is not a simple list/tuple, we
            //   consider it "custom/unsupported" and skip the inspection
            //   entirely to avoid fighting with user code.

            val dunderAllAssignment = findDunderAllAssignment(initFile)
            val dunderAllNames: Collection<String> = if (dunderAllAssignment == null) {
                emptyList()
            } else {
                when (val value = dunderAllAssignment.assignedValue) {
                    null -> emptyList()
                    is PySequenceExpression -> value.elements
                        .mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }

                    else -> return
                }
            }

            // 1) Symbols defined directly in __init__.py
            for (element in initFile.iterateNames()) {
                if (!isExportable(element)) continue
                if (isAllowlistedSymbol(element)) continue

                val name = element.name
                if (name == null || StringUtil.isEmpty(name) || name.startsWith("_")) continue

                if (!dunderAllNames.contains(name)) {
                    val nameIdentifier = getNameIdentifier(element) ?: continue
                    holder.registerProblem(
                        nameIdentifier,
                        "Symbol '$name' is not exported in __all__",
                        PyAddSymbolToAllQuickFix(name),
                    )
                }
            }

            // 2) Imported symbols that should be part of the package API
            for (statement in initFile.statements) {
                val fromImport = statement as? PyFromImportStatement ?: continue

                for (importElement in fromImport.importElements) {
                    val importedQName = importElement.importedQName ?: continue
                    val name = importedQName.lastComponent ?: continue

                    if (name.isEmpty() || name.startsWith("_")) continue
                    if (dunderAllNames.contains(name)) continue

                    // Respect existing allowlists (e.g. test_ helpers)
                    if (allowlistedFunctionNamePrefixes.any { prefix -> name.startsWith(prefix) }) continue
                    if (allowlistedClassNamePrefixes.any { prefix -> name.startsWith(prefix) }) continue

                    val anchor = importElement.asNameElement ?: importElement
                    holder.registerProblem(
                        anchor,
                        "Symbol '$name' is not exported in __all__",
                        PyAddSymbolToAllQuickFix(name),
                    )
                }
            }
        }

        /**
         * New behaviour: for a regular module, ensure its public symbols are
         * exported from the containing package's __init__.py via __all__.
         */
        private fun checkModuleExportsFromContainingPackage(moduleFile: PyFile) {
            val directory = moduleFile.containingDirectory ?: return
            val packageInit = directory.findFile(PyNames.INIT_DOT_PY) as? PyFile ?: return

            if (isAllowlistedModule(moduleFile) || isAllowlistedModule(packageInit)) return

            // For the package __init__.py we want slightly different
            // semantics than for the in-file check in [checkInitFileExports]:
            //
            // - If there is *no* __all__ at all, we still want to report
            //   problems so that the quick-fix can create __all__ from
            //   scratch. This is the case exercised by
            //   ModuleMissingFromPackageAllFix_NoAll.
            // - If __all__ exists but is not a simple list/tuple of strings,
            //   we treat it as "custom/unsupported" and skip the
            //   cross-file check entirely to avoid fighting with user code.

            val dunderAllAssignment = findDunderAllAssignment(packageInit)
            val dunderAllNames = dunderAllAssignment?.let { findDunderAllNames(packageInit) }

            // There is some kind of __all__ assignment, but we could not
            // interpret it as a simple sequence – bail out.
            if (dunderAllAssignment != null && dunderAllNames == null) return

            // When there is no __all__ at all, behave as if it were an empty
            // list so that every suitable symbol in [moduleFile] is
            // considered missing and the quick-fix can create __all__.
            val exportedNames: Collection<String> = dunderAllNames ?: emptyList()

            for (element in moduleFile.iterateNames()) {
                if (!isExportable(element)) continue
                if (isAllowlistedSymbol(element)) continue

                val name = element.name
                if (name == null || StringUtil.isEmpty(name) || name.startsWith("_")) continue

                if (!exportedNames.contains(name)) {
                    // Prefer to highlight an existing re-export import in the
                    // package __init__.py when there is one ("usage site"
                    // inspection). When there is no such import yet,
                    // highlight the symbol at its
                    // declaration site in the implementation module. The
                    // quick-fix then uses the current editor file as context
                    // and updates the containing package's __init__.py.
                    val importStatement = findImportSymbol(element, moduleFile)
                    val problemElement: PsiElement = importStatement ?: (getNameIdentifier(element) ?: element)

                    // UX rule: exporting symbols via __all__ should be
                    // discoverable via the lightbulb both for public and
                    // private modules, but we only want a *warning* in the
                    // IDE when the implementation module itself is private
                    // (e.g. ``_client.py``). Public modules should still be
                    // able to invoke the export action manually via
                    // Alt+Enter on the symbol or import, but with a weak
                    // warning that doesn't clutter the UI.
                    val isPrivateModule = moduleFile.name.removeSuffix(".py").startsWith("_")
                    val highlightType = if (isPrivateModule) {
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    } else {
                        ProblemHighlightType.WEAK_WARNING
                    }

                    holder.registerProblem(
                        problemElement,
                        "Symbol '$name' is not exported in package __all__",
                        highlightType,
                        PyAddSymbolToAllQuickFix(name),
                    )
                }
            }
        }

        /**
         * Given a symbol defined in [moduleFile] and the containing package's
         * [target] file, locate the import statement in
         * {@code __init__.py} that brings this symbol into scope, if any.
         */
        private fun findImportSymbol(targetSymbol: PyElement, target: PyFile): PyImportStatementBase? {
            for (stmt in target.importBlock) {
                when (stmt) {
                    is PyFromImportStatement -> {
                        for (importElement in stmt.importElements) {
                            val importedMatches = importElement
                                .multiResolve()
                                .any { it.element == targetSymbol }

                            if (importedMatches) {
                                return stmt
                            }
                        }
                    }

                    is PyImportStatement -> {
                        for (importElement in stmt.importElements) {
                            val importedMatches = importElement
                                .multiResolve()
                                .any { it.element == targetSymbol }

                            if (importedMatches) {
                                return stmt
                            }
                        }
                    }
                }
            }

            return null
        }

        private fun isExportable(element: PyElement): Boolean =
            element is PyClass ||
                    element is PyFunction ||
                    (element is PyTargetExpression && PyNames.ALL != element.name) ||
                    element is PyTypeAliasStatement

        private fun isAllowlistedModule(file: PyFile): Boolean {
            val nameWithoutExtension = file.name.removeSuffix(".py")

            // Allowlist by module file name (e.g. test_something.py, tests.py)
            if (allowlistedExactModuleNames.contains(nameWithoutExtension)) return true
            if (allowlistedModuleNamePrefixes.any { prefix -> nameWithoutExtension.startsWith(prefix) }) {
                return true
            }

            // Also allowlist by containing package/directory name so that whole
            // test packages (e.g. `tests`, `test_package`) are ignored by the
            // inspection regardless of the individual module file names.
            val directoryName = file.containingDirectory?.name
            if (directoryName != null) {
                if (allowlistedExactModuleNames.contains(directoryName)) return true
                if (allowlistedModuleNamePrefixes.any { prefix -> directoryName.startsWith(prefix) }) {
                    return true
                }
            }

            return false
        }

        private fun isAllowlistedSymbol(element: PyElement): Boolean {
            val name = (element as? PsiNameIdentifierOwner)?.name ?: return false

            if (element is PyFunction && allowlistedFunctionNamePrefixes.any { prefix -> name.startsWith(prefix) }) {
                return true
            }

            if (element is PyClass && allowlistedClassNamePrefixes.any { prefix -> name.startsWith(prefix) }) {
                return true
            }

            return false
        }

        private fun getNameIdentifier(element: PyElement): PsiElement? =
            if (element is PsiNameIdentifierOwner) element.nameIdentifier else element

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

        /**
         * Locate a simple sequence __all__ assignment in [file] and return the
         * collection of exported names, or {@code null} if __all__ is not
         * present or not a simple list/tuple.
         */
        private fun findDunderAllNames(file: PyFile): Collection<String>? {
            // Used for checking regular modules against the containing
            // package's __init__.py. If there is no __all__ assignment or it
            // is not a simple list/tuple of strings, we return null so that
            // callers can skip the package-level check entirely.
            val dunderAllAssignment = findDunderAllAssignment(file) ?: return null
            return when (val value = dunderAllAssignment.assignedValue) {
                is PySequenceExpression -> value.elements
                    .mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }

                else -> null
            }
        }
    }
}
