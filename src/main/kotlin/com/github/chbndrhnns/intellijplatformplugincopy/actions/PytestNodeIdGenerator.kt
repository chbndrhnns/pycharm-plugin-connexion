package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.extensions.getQName
import com.jetbrains.python.psi.*
import com.jetbrains.python.testing.PyTestsLocator
import java.util.regex.Pattern

object PytestNodeIdGenerator {

    private val PROTOCOL_PATTERN = Pattern.compile("^python<([^<>]+)>$")

    fun parseProxy(proxy: SMTestProxy, project: Project): PytestTestRecord? {
        val locationUrl = proxy.locationUrl ?: return null
        val metainfo = proxy.metainfo

        // Fast path: many proxies can resolve their PSI location directly without re-parsing the URL.
        // This is both cheaper and avoids file-index work on EDT.
        val directElement = proxy.getLocation(project, GlobalSearchScope.projectScope(project))?.psiElement
        if (directElement != null) {
            val file = directElement.containingFile?.virtualFile ?: return null
            val nodeid = calculateNodeId(directElement, file, project, metainfo, pathFqn = null)
            return PytestTestRecord(
                nodeid = nodeid,
                psiElement = directElement,
                file = file,
                locationUrl = locationUrl,
                metainfo = metainfo
            )
        }

        // 1. Split protocol and path
        if (!locationUrl.contains("://")) return null

        val protocol = locationUrl.substringBefore("://")
        val pathFqn = locationUrl.substringAfter("://") // e.g. "tests.test_fqn.TestClass.test_method"

        // 2. Find Module & Resolve Element
        val module = findModuleFromProtocol(protocol, project) ?: return null
        val scope = module.getModuleWithDependenciesAndLibrariesScope(true)

        val locations = PyTestsLocator.getLocation(
            protocol,
            pathFqn,
            metainfo,
            project,
            scope
        )

        val element = locations.firstOrNull()?.psiElement
        val file = element?.containingFile?.virtualFile ?: return null

        // 3. Calculate Node ID (Pass pathFqn)
        val nodeid = calculateNodeId(element, file, project, metainfo, pathFqn)

        return PytestTestRecord(
            nodeid = nodeid,
            psiElement = element,
            file = file,
            locationUrl = locationUrl,
            metainfo = metainfo
        )
    }

    fun fromPsiElement(element: PsiElement, project: Project): String? {
        val file = element.containingFile?.virtualFile ?: return null
        return calculateNodeId(
            element = element,
            file = file,
            project = project,
            metainfo = null,
            pathFqn = null
        )
    }

    fun fromCaretElement(elementAtCaret: PsiElement, project: Project): String? {
        val offset = elementAtCaret.textOffset

        val function = PsiTreeUtil.getParentOfType(elementAtCaret, PyFunction::class.java, false)
        if (function != null) {
            val base = fromPsiElement(function, project) ?: return null
            val paramId = inferParametrizedIdFromCaret(function, elementAtCaret)
            return if (paramId != null) "$base[$paramId]" else base
        }

        val clazz = PsiTreeUtil.getParentOfType(elementAtCaret, PyClass::class.java, false)
        if (clazz != null) {
            return fromPsiElement(clazz, project)
        }

        // Fallback: when caret is inside a decorator PSI subtree, the nearest PyFunction/PyClass
        // might not be reachable directly from the leaf (depending on stubs/PSI structure).
        val decorator = PsiTreeUtil.getParentOfType(elementAtCaret, PyDecorator::class.java, false)
        if (decorator != null) {
            val decoratable = PsiTreeUtil.getParentOfType(decorator, PyDecoratable::class.java, false)
            when (decoratable) {
                is PyFunction -> {
                    val base = fromPsiElement(decoratable, project) ?: return null
                    val paramId = inferParametrizedIdFromCaret(decoratable, elementAtCaret)
                    return if (paramId != null) "$base[$paramId]" else base
                }

                is PyClass -> return fromPsiElement(decoratable, project)
            }
        }

        // Last-resort: locate the top-level container that spans this offset.
        // This covers PSI shapes where decorator arguments are not nested under the function/class.
        // This is used for jumping non-leaf elements in the test tree
        val pyFile = elementAtCaret.containingFile as? PyFile
        if (pyFile != null) {
            val topLevelFunction = pyFile.topLevelFunctions.firstOrNull { fn ->
                fn.textRange.containsOffset(offset) || fn.decoratorList?.textRange?.containsOffset(offset) == true
            }
            if (topLevelFunction != null) {
                val base = fromPsiElement(topLevelFunction, project) ?: return null
                val paramId = inferParametrizedIdFromCaret(topLevelFunction, elementAtCaret)
                return if (paramId != null) "$base[$paramId]" else base
            }

            val topLevelClass = pyFile.topLevelClasses.firstOrNull { cls ->
                cls.textRange.containsOffset(offset) || cls.decoratorList?.textRange?.containsOffset(offset) == true
            }
            if (topLevelClass != null) {
                return fromPsiElement(topLevelClass, project)
            }
        }

        return null
    }

    private fun inferParametrizedIdFromCaret(function: PyFunction, elementAtCaret: PsiElement): String? {
        val decorators = function.decoratorList?.decorators ?: return null

        val parametrizeDecorator = decorators.firstOrNull { decorator ->
            val callee = decorator.callee as? PyQualifiedExpression
            val qName = callee?.asQualifiedName()?.toString()
            qName == "pytest.mark.parametrize" ||
                    qName == "_pytest.mark.parametrize" ||
                    qName?.endsWith(".pytest.mark.parametrize") == true
        } ?: return null

        val argList = parametrizeDecorator.argumentList ?: return null
        val args = argList.arguments

        // Typical: parametrize("arg", [1, 2, 3])
        val valuesArg =
            args.drop(1).firstOrNull { it is PyListLiteralExpression || it is PyTupleExpression } as? PsiElement
                ?: return null

        val offset = elementAtCaret.textOffset
        if (!valuesArg.textRange.containsOffset(offset)) return null

        val selectedValue = when (valuesArg) {
            is PyListLiteralExpression -> valuesArg.elements.firstOrNull { it.textRange.containsOffset(offset) }
            is PyTupleExpression -> valuesArg.elements.firstOrNull { it.textRange.containsOffset(offset) }
            else -> null
        } ?: return null

        return when (selectedValue) {
            is PyStringLiteralExpression -> selectedValue.stringValue
            else -> selectedValue.text
        }
    }

    private fun findModuleFromProtocol(protocol: String, project: Project): Module? {
        val matcher = PROTOCOL_PATTERN.matcher(protocol)
        if (!matcher.matches()) return null

        val filePath = matcher.group(1)
        var virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (virtualFile == null) {
            virtualFile = VirtualFileManager.getInstance().findFileByUrl("temp://$filePath")
        }

        if (virtualFile == null) return null

        return com.intellij.openapi.roots.ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)
    }

    private fun calculateNodeId(
        element: PsiElement,
        file: VirtualFile,
        project: Project,
        metainfo: String?,
        pathFqn: String?
    ): String {
        // A. File Part (Relative path with / separator)
        val relativePath = projectRelativePath(file, project)

        // B. Suffix Calculation

        // STRATEGY 1: FQN String Subtraction (Preferred)
        // If we can determine the file's QName (e.g. "tests.test_fqn"), we can
        // subtract it from the full FQN ("tests.test_fqn.Class.test") to get "Class.test".
        val pyFile = element.containingFile as? PyFile
        val fileQName = pyFile?.getQName()

        if (!pathFqn.isNullOrBlank() && fileQName != null) {
            val pathQN = QualifiedName.fromDottedString(pathFqn)

            // Check if the path actually starts with the file's QName
            if (pathQN.componentCount > fileQName.componentCount &&
                pathQN.subQualifiedName(0, fileQName.componentCount) == fileQName
            ) {

                // Extract the remainder: Class.Method
                val suffixQN = pathQN.subQualifiedName(fileQName.componentCount, pathQN.componentCount)
                val parts = suffixQN.components.toMutableList()

                // Replace the leaf name with metainfo if available (to handle [params])
                if (parts.isNotEmpty() && !metainfo.isNullOrEmpty()) {
                    parts[parts.lastIndex] = metainfo
                }

                return "$relativePath::${parts.joinToString("::")}"
            }
        }

        // STRATEGY 2: PSI Traversal (Fallback)
        // Used if file QName cannot be determined or doesn't match URL.
        // We must be careful not to blindly rename the 'current' element to metainfo
        // unless we are sure it matches.
        val parts = mutableListOf<String>()
        var current: PsiElement? = element

        while (current != null && current !is PyFile) {
            if (current is PyClass || current is PyFunction) {
                var name = (current as? PsiNamedElement)?.name

                // Apply metainfo only if this is the start element AND names look compatible
                // (This avoids renaming the Class to the Method name if resolution stopped early)
                if (current == element && !metainfo.isNullOrEmpty()) {
                    // Simple heuristic: if metainfo contains name (e.g. test_foo[p] contains test_foo)
                    // or if we trust resolution is perfect.
                    // Here we default to PSI name to be safe against the "Missing Hierarchy" bug
                    // unless we are strictly at the leaf.

                    // If you strictly want metainfo for the leaf:
                    if (name != null && metainfo.startsWith(name)) {
                        name = metainfo
                    }
                }

                if (name != null) {
                    parts.add(0, name)
                }
            }
            current = current.parent
        }

        if (parts.isEmpty()) return relativePath
        return "$relativePath::${parts.joinToString("::")}" 
    }

    private fun projectRelativePath(file: VirtualFile, project: Project): String {
        val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
        val rel = contentRoot?.let { root -> VfsUtilCore.getRelativePath(file, root) }
        return rel ?: file.path
    }
}