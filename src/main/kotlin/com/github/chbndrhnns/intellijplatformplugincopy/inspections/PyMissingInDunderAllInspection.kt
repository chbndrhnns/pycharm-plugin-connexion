package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
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

            if (node.name == PyNames.INIT_DOT_PY) {
                checkInitFileExports(node)
            } else {
                checkModuleExportsFromContainingPackage(node)
            }
        }

        /**
         * Original behaviour: ensure symbols defined in __init__.py are
         * exported via that file's own __all__.
         */
        private fun checkInitFileExports(initFile: PyFile) {
            if (isAllowlistedModule(initFile)) return
            // remove. Silly
            val dunderAllNames = findDunderAllNames(initFile) ?: return

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
        }

        /**
         * New behaviour: for a regular module, ensure its public symbols are
         * exported from the containing package's __init__.py via __all__.
         */
        private fun checkModuleExportsFromContainingPackage(moduleFile: PyFile) {
            val directory = moduleFile.containingDirectory ?: return
            val packageInit = directory.findFile(PyNames.INIT_DOT_PY) as? PyFile ?: return

            if (isAllowlistedModule(moduleFile) || isAllowlistedModule(packageInit)) return

            val dunderAllNames = findDunderAllNames(packageInit) ?: return

            for (element in moduleFile.iterateNames()) {
                if (!isExportable(element)) continue
                if (isAllowlistedSymbol(element)) continue

                val name = element.name
                if (name == null || StringUtil.isEmpty(name) || name.startsWith("_")) continue

                if (!dunderAllNames.contains(name)) {
                    // The inspection for a regular module is run on that
                    // module file itself. ProblemDescriptors created here must
                    // therefore be anchored to elements from [moduleFile]
                    // (typically the symbol definition), not from the
                    // containing package's __init__.py.
                    val nameIdentifier = getNameIdentifier(element) ?: moduleFile

                    holder.registerProblem(
                        nameIdentifier,
                        "Symbol '$name' is not exported in package __all__",
                        PyAddSymbolToAllQuickFix(name),
                    )
                }
            }
        }

        private fun isExportable(element: PyElement): Boolean =
            element is PyClass ||
                    element is PyFunction ||
                    (element is PyTargetExpression && !PyNames.ALL.equals(element.name)) ||
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
            val dunderAllAssignment = findDunderAllAssignment(file) ?: return emptyList()
            return when (val value = dunderAllAssignment.assignedValue) {
                null -> emptyList()
                is PySequenceExpression -> value.elements
                    .mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }

                else -> null
            }
        }
    }
}
