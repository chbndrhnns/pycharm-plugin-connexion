package com.github.chbndrhnns.intellijplatformplugincopy.features.exports

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFile

/** Quick-fix that adds a symbol to __all__ in a package __init__.py.
 *
 * Behaviour:
 * - If a simple sequence __all__ assignment exists, append the missing
 *   symbol name to that sequence.
 * - If __all__ is missing entirely, create a new
 *   `__all__ = ["name"]` assignment near the top of the file.
 * - If __all__ exists but is not a sequence, the inspection does not
 *   register problems, so this fix is never invoked in that case.
 */
class PyAddSymbolToAllQuickFix(private val name: String) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String = "BetterPy: Add to __all__"

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val contextFile = element.containingFile ?: return
        val contextPyFile = contextFile as? PyFile ?: return

        // Decide which file's __all__ should be updated:
        // - If we are already in __init__.py, operate on this file.
        // - Otherwise, try to locate the containing package's __init__.py
        //   and update its __all__ (cross-file quick-fix behaviour).
        val (targetFile, sourceModule) = if (contextPyFile.name == PyNames.INIT_DOT_PY) {
            // We are already in the package __init__.py – operate on this file.
            (updater.getWritable(contextPyFile) as PyFile) to null
        } else {
            // We are in a regular module. Locate the containing package's __init__.py.
            // If the immediate containing package is private (starts with '_'),
            // traverse up to find the nearest public package (or the root-most private package).
            
            // Use VFS to navigate to ensure we handle test fixtures correctly (where PSI might differ).
            val vFile = PsiUtilCore.getVirtualFile(element) ?: return
            var parentVFile = vFile.parent ?: return
            
            while (parentVFile.name.startsWith("_")) {
                val nextParent = parentVFile.parent ?: break
                // Stop traversal if the parent is not a package (no __init__.py)
                if (nextParent.findChild(PyNames.INIT_DOT_PY) == null) break
                parentVFile = nextParent
            }
            
            val parentPsiDir = PsiManager.getInstance(project).findDirectory(parentVFile) ?: return
            val initFile = parentPsiDir.findFile(PyNames.INIT_DOT_PY) as? PyFile ?: return
            (updater.getWritable(initFile) as PyFile) to contextPyFile
        }

        PyAllExportUtil.ensureSymbolExported(project, targetFile, name, sourceModule)
    }
}