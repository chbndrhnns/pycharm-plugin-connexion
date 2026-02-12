package com.github.chbndrhnns.betterpy.features.exports

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonFileType
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

        // 1) Resolve the imported source module and find the nearest
        //    containing package __init__.py where we should export from.
        val sourceModule = fromImport.resolveImportSource() as? PyFile ?: return
        val directory = sourceModule.containingDirectory ?: return
        val initFile = directory.findFile(PyNames.INIT_DOT_PY) as? PyFile ?: return
        val file = fromImport.containingFile as? PyFile ?: return

        // Collect all from-import statements that import the same symbol
        // from the same private module, excluding the package that performs
        // the export itself.
        val importsToRewrite = mutableListOf<PyFromImportStatement>()

        fun collectIfMatches(targetFile: PyFile, stmt: PyFromImportStatement) {
            if (stmt.isStarImport) return
            val source = stmt.resolveImportSource() as? PyFile ?: return
            if (source != sourceModule) return

            // Ensure the symbol is among the imported names.
            val hasName = stmt.importElements.any { it.importedQName?.lastComponent == importedName }
            if (!hasName) return

            importsToRewrite += stmt
        }

        // Always include the original import site.
        collectIfMatches(file, fromImport)

        // Scan other Python files in the project and collect matching
        // imports, but do not touch any references in the same package
        // where the export happens.
        val psiManager = PsiManager.getInstance(project)
        val projectScope = GlobalSearchScope.projectScope(project)

        for (vFile in FileTypeIndex.getFiles(PythonFileType.INSTANCE, projectScope)) {
            val otherFile = psiManager.findFile(vFile) as? PyFile ?: continue
            val otherDir = otherFile.containingDirectory

            if (otherFile == initFile || otherDir == directory) {
                // Skip the package __init__.py and any modules inside the
                // exporting package; their private imports are intentional.
                continue
            }

            for (statement in otherFile.statements) {
                val otherFrom = statement as? PyFromImportStatement ?: continue
                collectIfMatches(otherFile, otherFrom)
            }
        }

        // Request writable copies for all affected elements *before* any
        // modifications are performed, as required by the ModCommand
        // framework.
        val writableInit = updater.getWritable(initFile) as PyFile
        val writableFromImports = importsToRewrite.map { stmt ->
            updater.getWritable(stmt) as PyFromImportStatement
        }

        // Ensure the symbol is exported via __all__ and a re-export import
        // in the package __init__.py.
        PyAllExportUtil.ensureSymbolExported(project, writableInit, importedName, sourceModule)

        // Rewrite all collected imports to use the package export.
        for (writableFromImport in writableFromImports) {
            val containing = writableFromImport.containingFile as? PyFile ?: continue
            maybeRewriteImportForSymbol(project, containing, writableFromImport, importedName)
        }
    }

    private fun maybeRewriteImportForSymbol(
        project: Project,
        file: PyFile,
        fromImport: PyFromImportStatement,
        importedName: String,
    ) {
        for (element in fromImport.importElements) {
            val name = element.importedQName?.lastComponent ?: continue
            if (name != importedName) continue

            rewriteImportToUsePackage(project, file, fromImport, element)
            break
        }
    }

    private fun rewriteImportToUsePackage(
        project: Project,
        file: PyFile,
        fromImport: PyFromImportStatement,
        importElement: PyImportElement,
    ) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(file)

        val importSource = fromImport.importSource ?: return

        // ``importSource`` may be a qualified expression (for absolute
        // imports) or a simple reference expression. We rewrite the text
        // representation conservatively by dropping the last dotted
        // component when it starts with an underscore.
        val referencedName = importSource.referencedName
        if (referencedName == null || !referencedName.startsWith("_")) {
            return
        }
        val newSourceText = importSource.qualifier?.text ?: return

        val alias = importElement.asName

        val relativeLevel = fromImport.relativeLevel
        val newImportText = if (relativeLevel > 0) {
            // For relative imports we prepend the appropriate number of
            // leading dots to the new source text.
            val dots = "".padStart(relativeLevel, '.')
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

        val hasOtherImports = fromImport.importElements.size > 1
        if (hasOtherImports) {
            // When the statement imports multiple symbols, only remove the
            // target element and add a new import statement for the
            // rewritten symbol, preserving the other imports.
            importElement.delete()
            fromImport.parent.addAfter(newFromImport, fromImport)
        } else {
            fromImport.replace(newFromImport)
        }
    }

    private fun buildImportedFragment(name: String, alias: String?): String =
        if (alias != null) "$name as $alias" else name
}