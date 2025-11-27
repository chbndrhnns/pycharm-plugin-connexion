package com.github.chbndrhnns.intellijplatformplugincopy.inspections

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
    ): PsiElementVisitor = Visitor(holder, session)

    private class Visitor(
        private val holder: ProblemsHolder,
        private val session: LocalInspectionToolSession,
    ) : PyElementVisitor() {

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
            // remove. Silly
            val dunderAllNames = findDunderAllNames(initFile) ?: return

            for (element in initFile.iterateNames()) {
                if (!isExportable(element)) continue

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

            val dunderAllNames = findDunderAllNames(packageInit) ?: return

            for (element in moduleFile.iterateNames()) {
                if (!isExportable(element)) continue

                val name = element.name
                if (name == null || StringUtil.isEmpty(name) || name.startsWith("_")) continue

                if (!dunderAllNames.contains(name)) {
                    val nameIdentifier = getNameIdentifier(element) ?: continue
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
