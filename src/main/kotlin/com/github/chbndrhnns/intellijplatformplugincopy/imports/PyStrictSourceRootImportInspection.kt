package com.github.chbndrhnns.intellijplatformplugincopy.imports

import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*

class PyStrictSourceRootImportInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!PythonVersionGuard.isSatisfied(holder.project)) {
            return object : PyElementVisitor() {}
        }
        val settings = PluginSettingsState.instance().state
        if (!settings.enableRestoreSourceRootPrefix) {
            return object : PyElementVisitor() {}
        }

        return object : PyElementVisitor() {
            override fun visitPyFromImportStatement(node: PyFromImportStatement) {
                super.visitPyFromImportStatement(node)
                if (node.relativeLevel > 0) return
                checkImportSource(node.importSource, holder)
            }

            override fun visitPyImportStatement(node: PyImportStatement) {
                super.visitPyImportStatement(node)
                for (importElement in node.importElements) {
                    checkImportSource(importElement.importReferenceExpression, holder)
                }
            }
        }
    }

    private fun checkImportSource(importSource: PyReferenceExpression?, holder: ProblemsHolder) {
        if (importSource == null) return
        val qName = importSource.asQualifiedName() ?: return
        
        // Use resolveImportSource to handle package-level resolution correctly
        val resolved = if (importSource.parent is PyFromImportStatement) {
            (importSource.parent as PyFromImportStatement).resolveImportSource()
        } else {
            importSource.reference.resolve()
        } ?: return
        
        val virtualFile = PsiUtilCore.getVirtualFile(resolved) ?: return
        
        val project = importSource.project
        val fileIndex = ProjectFileIndex.getInstance(project)
        val root = fileIndex.getSourceRootForFile(virtualFile) ?: return
        val contentRoot = fileIndex.getContentRootForFile(virtualFile) ?: return

        if (root == contentRoot) return
        if (!VfsUtilCore.isAncestor(contentRoot, root, true)) return

        val prefixPath = VfsUtilCore.getRelativePath(root, contentRoot)
        if (prefixPath.isNullOrEmpty()) return

        val components = prefixPath.split('/')
        val prefix = QualifiedName.fromComponents(components)
        
        // If the current qName doesn't start with the required prefix, it's a problem
        if (!qName.matchesPrefix(prefix)) {
            holder.registerProblem(
                importSource,
                "Import is missing source root prefix '$prefix'",
                com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                PyPrependSourceRootQuickFix(prefix.toString())
            )
        }
    }
}

class PyPrependSourceRootQuickFix(private val prefix: String) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String = "Prepend source root prefix"

    override fun getName(): String = "Prepend source root prefix '$prefix'"

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val importSource = element as? PyReferenceExpression ?: return
        val qName = importSource.asQualifiedName() ?: return
        val newQName = QualifiedName.fromDottedString(prefix).append(qName)
        
        val generator = PyElementGenerator.getInstance(project)
        val newReference = generator.createExpressionFromText(LanguageLevel.forElement(element), newQName.toString()) as PyReferenceExpression
        
        updater.getWritable(importSource).replace(newReference)
    }
}
