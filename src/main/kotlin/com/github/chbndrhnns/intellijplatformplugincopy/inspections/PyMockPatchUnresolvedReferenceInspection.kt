package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.JbPopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.psi.PyResolveUtils
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
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
        return object : PyElementVisitor() {
            override fun visitPyCallExpression(node: PyCallExpression) {
                super.visitPyCallExpression(node)

                if (!PluginSettingsState.instance().state.enablePyMockPatchReferenceContributor) return

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
                val firstSegment = segments[0]
                val resolved = PyResolveUtils.resolveDottedName(firstSegment, targetArg)

                if (resolved == null) {
                    val candidates = findCandidates(node.project, firstSegment)
                    val fqns = candidates.mapNotNull { getFQN(it) }.distinct()

                    if (fqns.isNotEmpty()) {
                        holder.registerProblem(
                            targetArg,
                            "Unresolved reference '$firstSegment' in patch target",
                            PyMockPatchReplaceWithFQNQuickFix(firstSegment, fqns)
                        )
                    }
                }
            }
        }
    }

    private fun isPatchCall(call: PyCallExpression): Boolean {
        val callee = call.callee as? PyReferenceExpression ?: return false
        return callee.referencedName == "patch" && isFromUnittestMock(callee)
    }

    private fun isFromUnittestMock(reference: PyReferenceExpression): Boolean {
        // In tests, the reference might be resolved to our mocked unittest/mock.py
        val resolveResults = reference.getReference().multiResolve(false)
        if (resolveResults.isEmpty()) {
            return reference.referencedName == "patch"
        }
        for (result in resolveResults) {
            val element = result.element
            if (element is PyFunction && element.name == "patch") {
                return true
            }
            if (element is PyTargetExpression && element.name == "patch") {
                return true
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
                .filter { it.parent?.name == name }
                .mapNotNull { it.parent }
            result.addAll(packages)

            // Try searching for the name as a directory directly if it's a package
            val dirs = FilenameIndex.getAllFilesByExt(project, "", scope)
                .filter { it.isDirectory && it.name == name && it.findChild("__init__.py") != null }
            result.addAll(dirs.mapNotNull { com.intellij.psi.PsiManager.getInstance(project).findDirectory(it) })
        } catch (e: Exception) {
        }

        return result
    }

    private fun getFQN(element: PsiElement): String? {
        val includeSourceRootPrefix = PluginSettingsState.instance().state.enableRestoreSourceRootPrefix

        val fqn = when (element) {
            is PsiDirectory ->
                QualifiedNameFinder.findCanonicalImportPath(element, null)?.toString() ?: element.name

            is PyQualifiedNameOwner -> element.qualifiedName
            is PyFile -> QualifiedNameFinder.findCanonicalImportPath(element, null)?.toString()
            else -> null
        } ?: return null

        if (includeSourceRootPrefix) {
            val project = element.project
            val file = when (element) {
                is PyFile -> element.virtualFile
                is PsiDirectory -> element.virtualFile
                else -> element.containingFile?.virtualFile
            }
            if (file != null) {
                val fileIndex = ProjectFileIndex.getInstance(project)
                val sourceRoot = fileIndex.getSourceRootForFile(file)
                val contentRoot = fileIndex.getContentRootForFile(file)
                if (sourceRoot != null && contentRoot != null) {
                    val prefixPath = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '.')
                    if (!prefixPath.isNullOrEmpty()) {
                        return "$prefixPath.$fqn"
                    }
                }
            }
        }

        return fqn
    }

    override fun getStaticDescription(): String =
        "Checks if the first segment of a mock.patch target resolves and provides a quickfix to use the FQN if it doesn't."
}

class PyMockPatchReplaceWithFQNQuickFix(private val name: String, private val fqns: List<String>) : LocalQuickFix {
    override fun getName(): String {
        return if (fqns.size == 1) "Replace '$name' with '${fqns[0]}'"
        else "Replace '$name' with FQN..."
    }

    override fun getFamilyName(): String = "Replace with FQN"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? PyStringLiteralExpression ?: return

        if (fqns.size == 1) {
            applyReplacement(project, element, fqns[0])
        } else {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
            JbPopupHost().showChooser(
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
        val currentPath = element.stringValue
        val newPath = if (currentPath.contains('.')) {
            val suffix = currentPath.substring(currentPath.indexOf('.'))
            fqn + suffix
        } else {
            fqn
        }

        val generator = PyElementGenerator.getInstance(project)
        val newLiteral = generator.createStringLiteralFromString(newPath)
        element.replace(newLiteral)
    }
}
