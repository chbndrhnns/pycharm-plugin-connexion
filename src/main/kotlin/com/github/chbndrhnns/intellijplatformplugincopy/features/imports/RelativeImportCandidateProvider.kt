package com.github.chbndrhnns.intellijplatformplugincopy.features.imports

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.psi.PsiReference
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix
import com.jetbrains.python.codeInsight.imports.PyImportCandidateProvider
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

class RelativeImportCandidateProvider : PyImportCandidateProvider {
    override fun addImportCandidates(reference: PsiReference, name: String, quickFix: AutoImportQuickFix) {
        if (!PluginSettingsState.instance().state.enableRelativeImportPreference) {
            return
        }

        val file = reference.element.containingFile as? PyFile ?: return
        val fileParent = file.parent
        // Check if the current file is inside the package
        if (fileParent == null || !PyUtil.isPackage(fileParent, file)) return

        // Gets the Qualified Name of the current package (directory) (e.g., "pkg")
        val parentQName = QualifiedNameFinder.findShortestImportableQName(fileParent) ?: return

        // Iterate over a copy to avoid ConcurrentModificationException when adding new candidates
        for (candidate in ArrayList(quickFix.candidates)) {
            val importable = candidate.importable ?: continue
            val importableFile = importable.containingFile ?: continue

            // Get the qualified name of the target file (e.g., "pkg.sub")
            val importableQName = QualifiedNameFinder.findShortestImportableQName(importableFile) ?: continue

            // Check if the target is a child of the current package (prefix match)
            if (importableQName.matchesPrefix(parentQName)) {
                // Extract only the relative path part (e.g., "pkg.sub" - "pkg" -> "sub")
                val relativePart = importableQName.removeHead(parentQName.componentCount)

                // QualifiedName Component Configuration
                val components = ArrayList<String>()
                components.add("") // The first empty string means the preceding dot (.).

                if (relativePart.componentCount == 0) {
                    // If it's the same package (e.g., from . import ...)
                    // QualifiedName(["", ""]) converts to "."
                    components.add("")
                } else {
                    // If it is a sub-module (e.g., from .sub import ...)
                    // QualifiedName(["", "sub"]) is converted to ".sub"
                    components.addAll(relativePart.components)
                }

                val relativePath = QualifiedName.fromComponents(components)
                quickFix.addImport(importable, file, relativePath)
            }
        }
    }
}
