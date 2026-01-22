package com.github.chbndrhnns.intellijplatformplugincopy.features.inspections

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

/**
 * Quick-fix that rewrites an import from a private implementation module to
 * the public package export, e.g.:
 *
 *     from .mypackage._lib import Client
 *
 * becomes
 *
 *     from .mypackage import Client
 *
 * when ``Client`` is exported from ``mypackage.__init__.__all__``.
 */
class PyUseExportedSymbolFromPackageQuickFix(
    private val importedName: String,
) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String = "Use exported symbol from package instead of private module"

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        // The problem may be anchored either at the individual PyImportElement
        // or at the whole PyFromImportStatement. Support both.
        val (fromImport, importElement) = when (element) {
            is PyImportElement -> (element.parent as? PyFromImportStatement)?.let { it to element } ?: return
            is PyFromImportStatement -> {
                // Find the matching import element by the imported name this quick-fix was created for.
                val match = element.importElements.firstOrNull { it.importedQName?.lastComponent == importedName }
                    ?: return
                element to match
            }

            else -> return
        }

        val file = fromImport.containingFile as? PyFile ?: return

        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(file)

        val importSource = fromImport.importSource ?: return

        // ``importSource`` may be a qualified expression (for absolute
        // imports) or a simple reference expression. We rewrite the text
        // representation conservatively by dropping the last dotted
        // component when it starts with an underscore.
        //
        // Update: We now strip ALL trailing private components to support
        // deep imports like 'mypkg._priv._impl'.
        var current: PyExpression? = importSource
        while (current is PyQualifiedExpression) {
            val name = current.referencedName
            if (name == null || !name.startsWith("_")) {
                break
            }
            // Strip the private component
            current = current.qualifier
        }

        // If 'current' is null, it means we stripped everything (e.g. 'from ._lib').
        val newSourceText = current?.text ?: ""

        // If we didn't change anything, we abort (though inspection logic ensures we start with a private module).
        if (newSourceText == importSource.text) {
            return
        }

        val alias = importElement.asName

        val newImportText = if (fromImport.relativeLevel > 0) {
            // For relative imports we prepend the appropriate number of
            // leading dots to the new source text.
            val dots = "".padStart(fromImport.relativeLevel, '.')
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

        // Replace the entire from-import statement so that we handle
        // multiple imported names consistently (for now this quick-fix is
        // only offered for single-name imports in tests).
        updater.getWritable(fromImport).replace(newFromImport)

        // The ModCommand framework will take care of re-highlighting; no
        // explicit PSI refresh call is required here.
    }

    private fun buildImportedFragment(name: String, alias: String?): String =
        if (alias != null) "$name as $alias" else name
}
