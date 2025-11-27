package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*

/**
 * Quick-fix that makes a symbol public by exporting it from the nearest
 * package ``__init__.py`` (updating ``__all__`` and adding a re-export
 * import), and then rewrites the original import to use the public package
 * export instead of the private implementation module.
 *
 * Example:
 *
 *     # mypackage/_lib.py
 *     class Client:
 *         ...
 *
 *     # cli.py
 *     from .mypackage._lib import Client
 *
 * becomes
 *
 *     # mypackage/__init__.py
 *     __all__ = ['Client']
 *     from ._lib import Client
 *
 *     # cli.py
 *     from .mypackage import Client
 */
class PyMakeSymbolPublicAndUseExportedSymbolQuickFix(
    private val importedName: String,
) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String = "Make symbol public and import from package"

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val importElement = element as? PyImportElement ?: return
        val fromImport = importElement.parent as? PyFromImportStatement ?: return

        val file = fromImport.containingFile as? PyFile ?: return

        // 1) Resolve the imported source module and find the nearest
        //    containing package __init__.py where we should export from.
        val sourceModule = fromImport.resolveImportSource() as? PyFile ?: return
        val directory = sourceModule.containingDirectory ?: return
        val initFile = directory.findFile(PyNames.INIT_DOT_PY) as? PyFile ?: return

        val writableInit = updater.getWritable(initFile) as PyFile

        // 2) Ensure the symbol is exported via __all__ and a re-export
        //    import in the package __init__.py.
        PyAllExportUtil.ensureSymbolExported(project, writableInit, importedName, sourceModule)

        // 3) Rewrite the original import to use the package export, using
        //    the same conservative text-based approach as
        //    PyUseExportedSymbolFromPackageQuickFix.
        rewriteImportToUsePackage(project, file, fromImport, importElement, updater)
    }

    private fun rewriteImportToUsePackage(
        project: Project,
        file: PyFile,
        fromImport: PyFromImportStatement,
        importElement: PyImportElement,
        updater: ModPsiUpdater,
    ) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(file)

        val importSource = fromImport.importSource ?: return

        // ``importSource`` may be a qualified expression (for absolute
        // imports) or a simple reference expression. We rewrite the text
        // representation conservatively by dropping the last dotted
        // component when it starts with an underscore.
        val sourceText = importSource.text
        val newSourceText = when (importSource) {
            is PyQualifiedExpression -> {
                val qualifierText = importSource.qualifier?.text
                val referencedName = importSource.referencedName
                if (referencedName != null && referencedName.startsWith("_")) {
                    qualifierText ?: return
                } else {
                    // Not a private module after all – bail out.
                    return
                }
            }

            is PyReferenceExpression -> {
                val referencedName = importSource.referencedName
                if (referencedName != null && referencedName.startsWith("_")) {
                    // ``from _impl import Name`` → ``from . import Name`` is
                    // not what we want here; this quick-fix is meant for
                    // ``from pkg._impl import Name`` style imports where
                    // there is at least one public package component.
                    return
                }
                sourceText
            }

            else -> return
        }

        val alias = importElement.asName

        val newImportText = if (fromImport.relativeLevel != null && fromImport.relativeLevel!! > 0) {
            // For relative imports we prepend the appropriate number of
            // leading dots to the new source text.
            val dots = "".padStart(fromImport.relativeLevel!!, '.')
            val fromPart = if (newSourceText.isNotEmpty()) "$dots$newSourceText" else dots
            "from $fromPart import ${buildImportedFragment(importedName, alias)}"
        } else {
            "from $newSourceText import ${buildImportedFragment(importedName, alias)}"
        }

        val newFromImport = generator.createFromText(
            languageLevel,
            PyFromImportStatement::class.java,
            newImportText,
        )

        updater.getWritable(fromImport).replace(newFromImport)
    }

    private fun buildImportedFragment(name: String, alias: String?): String =
        if (alias != null) "$name as $alias" else name
}
