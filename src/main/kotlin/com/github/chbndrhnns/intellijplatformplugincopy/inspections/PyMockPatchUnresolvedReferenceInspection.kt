package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.JbPopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.psi.PyResolveUtils
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.util.isOwnCode
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex

class PyMockPatchUnresolvedReferenceInspection : PyInspection() {

    override fun getShortName(): String = "PyMockPatchUnresolvedReference"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        return object : PyElementVisitor() {
            override fun visitPyCallExpression(node: PyCallExpression) {
                super.visitPyCallExpression(node)

                if (!PluginSettingsState.instance().state.enablePyMockPatchUnresolvedReferenceInspection) return

                if (!isPatchCall(node)) {
                    return
                }

                val argumentList = node.argumentList ?: return
                val arguments = argumentList.arguments
                if (arguments.isEmpty()) return

                val targetArg = arguments[0] as? PyStringLiteralExpression ?: return
                val targetPath = targetArg.stringValue
                if (targetPath.isEmpty()) return

                val segments = targetPath.split('.')
                var unresolvedSegment: String? = null
                var suffix = ""

                for (i in segments.indices) {
                    val segment = segments[i]
                    if (segment.isEmpty()) continue
                    val pathUpToSegment = segments.subList(0, i + 1).joinToString(".")
                    val resolved = PyResolveUtils.resolveDottedName(pathUpToSegment, targetArg)

                    if (resolved.isEmpty()) {
                        unresolvedSegment = segment
                        if (i < segments.size - 1) {
                            suffix = "." + segments.subList(i + 1, segments.size).joinToString(".")
                        }
                        break
                    }
                }

                if (unresolvedSegment != null) {
                    val candidates = findCandidates(node.project, unresolvedSegment)
                    val importSites = candidates.flatMap { findImportSites(it) }

                    val allCandidates = candidates + importSites
                    val projectFileIndex = ProjectFileIndex.getInstance(node.project)
                    val filteredCandidates = allCandidates.filter { candidate ->
                        val file = candidate.containingFile?.virtualFile ?: return@filter true
                        !projectFileIndex.isInTestSourceContent(file)
                    }

                    val fqns = filteredCandidates.mapNotNull { getFQN(it) }.distinct()

                    if (fqns.isNotEmpty()) {
                        holder.registerProblem(
                            targetArg,
                            "Unresolved reference '$unresolvedSegment' in patch target",
                            PyMockPatchReplaceWithFQNQuickFix(unresolvedSegment, fqns, suffix)
                        )
                    } else {
                        holder.registerProblem(
                            targetArg,
                            "Unresolved reference '$unresolvedSegment' in patch target"
                        )
                    }
                }
            }
        }
    }

    private fun findImportSites(element: PsiElement): List<PsiElement> {
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        val result = mutableListOf<PsiElement>()

        val name = element.let { (it as? com.intellij.psi.PsiNamedElement)?.name }
        if (name == null) return emptyList()

        com.intellij.psi.search.searches.ReferencesSearch.search(element, scope).forEach { ref ->
            val refElement = ref.element
            val file = refElement.containingFile as? PyFile ?: return@forEach

            // If it's an import element, we definitely use it
            val importElement = com.intellij.psi.util.PsiTreeUtil.getParentOfType(refElement, PyImportElement::class.java)
            if (importElement != null) {
                result.add(ImportedSymbol(file, name))
                return@forEach
            }

            // Otherwise, check if the symbol is actually available in this file's namespace
            // A simple check: if we can resolve the name in this file to the same element.
            // But patching often happens on things that are imported.
            // If it's not imported, it might be used via qualifier like `os.path.exists`.
            // In that case, `exists` is NOT in the module namespace, but `os` is.
            // The user wants FQNs for replacing, so if they have `@patch('exists')`
            // and `exists` is used in `app/utils.py`, they might want `@patch('app.utils.exists')`.
            // This only makes sense if `exists` is imported in `app/utils.py`.
            
            // Let's stick to import sites for now but make it more robust.
            // If we found a reference that is NOT in an import, we could still look at the file.
            // But if it's not imported, `app.utils.exists` won't work for patch.
            
            // If the reference is part of a qualified expression, maybe they want the full qualified name in that module?
            // E.g. `app.utils.os.path.exists`.
        }
        return result
    }

    private class ImportedSymbol(val file: PyFile, private val symbolName: String) :
        com.intellij.psi.impl.FakePsiElement(), PyQualifiedNameOwner {
        override fun getParent(): PsiElement = file
        override fun getContainingFile(): com.intellij.psi.PsiFile = file
        override fun getQualifiedName(): String? {
            val fileFQN = QualifiedNameFinder.findCanonicalImportPath(file, null)?.toString() ?: return null
            return "$fileFQN.$symbolName"
        }

        override fun getName(): String = symbolName
    }

    private fun isPatchCall(call: PyCallExpression): Boolean {
        val callee = call.callee as? PyReferenceExpression ?: return false
        return callee.referencedName == "patch" && isFromUnittestMock(callee)
    }

    private fun isFromUnittestMock(reference: PyReferenceExpression): Boolean {
        val resolveResults = reference.getReference().multiResolve(false)
        if (resolveResults.isEmpty()) {
            return reference.referencedName == "patch"
        }
        for (result in resolveResults) {
            val element = result.element ?: continue
            if (element is PyFunction && element.name == "patch") {
                val qualifiedName = element.qualifiedName
                if (qualifiedName == "unittest.mock.patch" || qualifiedName == "mock.patch" || qualifiedName == "pytest_mock.plugin.MockerFixture.patch") {
                    return true
                }
                // Fallback for tests or cases where FQN is not fully available
                val containingFile = element.containingFile
                if (containingFile is PyFile) {
                    val fileName = containingFile.name
                    if (fileName == "mock.py" || fileName == "pytest_mock.py") {
                        return true
                    }
                }
            }
            if (element is PyTargetExpression && element.name == "patch") {
                val qualifiedName = element.qualifiedName
                if (qualifiedName == "unittest.mock.patch" || qualifiedName == "mock.patch") {
                    return true
                }
                // If it's a target expression, it might be an alias like 'from unittest import mock as patch'
                // or just 'import unittest.mock as patch'
                val containingFile = element.containingFile
                if (containingFile is PyFile) {
                    val fileName = containingFile.name
                    if (fileName == "mock.py") {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun findCandidates(project: Project, name: String): List<PsiElement> {
        val scope = GlobalSearchScope.allScope(project)
        val result = mutableListOf<PsiElement>()

        // Find classes
        try {
            val classes = PyClassNameIndex.find(name, project, scope)
            result.addAll(classes)
        } catch (e: Exception) {
        }

        // Find functions
        try {
            val functions = PyFunctionNameIndex.find(name, project, scope)
            result.addAll(functions)
        } catch (e: Exception) {
        }

        // Find modules
        try {
            val modules = FilenameIndex.getFilesByName(project, "$name.py", scope)
            result.addAll(modules)
            // Also try searching for directory with __init__.py (package)
            val packages = FilenameIndex.getFilesByName(project, "__init__.py", scope)
                .filter { 
                    it.parent?.name == name
                }
                .mapNotNull { it.parent }
            result.addAll(packages)

            // Try searching for the name as a directory directly if it's a package
            val dirs = FilenameIndex.getAllFilesByExt(project, "", scope)
                .filter { 
                    it.isDirectory && it.name == name && it.findChild("__init__.py") != null
                }
            result.addAll(dirs.mapNotNull { com.intellij.psi.PsiManager.getInstance(project).findDirectory(it) })
        } catch (e: Exception) {
        }

        return result
    }

    private fun getFQN(element: PsiElement): String? {
        val project = element.project
        val file = when (element) {
            is PyFile -> element.virtualFile
            is PsiDirectory -> element.virtualFile
            is ImportedSymbol -> element.file.virtualFile
            else -> element.containingFile?.virtualFile
        }

        val isInContent = file != null && ProjectFileIndex.getInstance(project).isInContent(file)

        val includeSourceRootPrefix = PluginSettingsState.instance().state.enableRestoreSourceRootPrefix

        val fqn = when (element) {
            is PsiDirectory ->
                QualifiedNameFinder.findCanonicalImportPath(element, null)?.toString() ?: element.name

            is PyQualifiedNameOwner -> element.qualifiedName
            is PyFile -> QualifiedNameFinder.findCanonicalImportPath(element, null)?.toString()
            else -> null
        } ?: return null

        if (!isInContent && element !is ImportedSymbol) {
            return null
        }

        if (includeSourceRootPrefix && file != null) {
            val fileIndex = ProjectFileIndex.getInstance(project)
            val sourceRoot = fileIndex.getSourceRootForFile(file)
            val contentRoot = fileIndex.getContentRootForFile(file)
            if (sourceRoot != null && contentRoot != null) {
                val prefixPath = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '.')
                if (!prefixPath.isNullOrEmpty()) {
                    if (fqn.startsWith("$prefixPath.") || fqn == prefixPath) {
                        return fqn
                    }
                    return "$prefixPath.$fqn"
                }
            }
        }

        return fqn
    }

    override fun getStaticDescription(): String =
        "Checks if all segments of a mock.patch target resolve and provides a quickfix to use the FQN if a segment doesn't."
}

class PyMockPatchReplaceWithFQNQuickFix(
    private val name: String,
    private val fqns: List<String>,
    private val suffix: String
) : LocalQuickFix {

    companion object {
        var popupHost: PopupHost? = null
    }

    override fun getName(): String {
        return if (fqns.size == 1) "BetterPy: Replace '$name' with '${fqns[0]}'"
        else "BetterPy: Replace '$name' with FQN..."
    }

    override fun getFamilyName(): String = "BetterPy: Replace with FQN"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? PyStringLiteralExpression ?: return

        if (fqns.size == 1) {
            applyReplacement(project, element, fqns[0])
        } else {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
            val host = popupHost ?: JbPopupHost()
            host.showChooser(
                editor,
                "Select FQN for '$name'",
                fqns,
                { it },
                { selectedFqn ->
                    WriteCommandAction.runWriteCommandAction(project) {
                        applyReplacement(project, element, selectedFqn)
                    }
                }
            )
        }
    }

    private fun applyReplacement(project: Project, element: PyStringLiteralExpression, fqn: String) {
        val newPath = fqn + suffix
        val generator = PyElementGenerator.getInstance(project)
        val newLiteral = generator.createStringLiteralFromString(newPath)
        element.replace(newLiteral)
    }
}
