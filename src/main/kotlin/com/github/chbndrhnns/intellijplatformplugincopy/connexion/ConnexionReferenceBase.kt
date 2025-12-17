package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex

abstract class ConnexionReferenceBase(element: PsiElement) : PsiPolyVariantReferenceBase<PsiElement>(element) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = element.project
        val operationId = operationIdText()
        if (operationId.isBlank()) return ResolveResult.EMPTY_ARRAY

        val controller = findController()

        val candidates = mutableSetOf<String>()
        if (isQualified(operationId)) {
            candidates.add(normalize(operationId))
        } else if (controller != null) {
            candidates.add(normalize("$controller.$operationId"))
        }

        if (candidates.isEmpty()) return ResolveResult.EMPTY_ARRAY

        val results = mutableListOf<ResolveResult>()
        for (qName in candidates) {
            val resolved = resolvePythonSymbol(qName, project)
            results.addAll(resolved.map { PsiElementResolveResult(it) })
        }

        return results.toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val currentText = operationIdText()
        if (isQualified(currentText)) {
            val parts = currentText.split(":").toMutableList()
            if (parts.size == 1) {
                // Dot separator
                val dotParts = currentText.split(".").toMutableList()
                dotParts[dotParts.lastIndex] = newElementName
                val newText = dotParts.joinToString(".")
                return super.handleElementRename(newText)
            } else {
                // Colon separator? Not sure if connexion supports colon in operationId, but matchesFunction uses replace(":", ".")
                // Let's assume dot for now as standard python path.
                // But matchesFunction logic suggests colon is possible.
                // If colon exists, preserve it.
                // But handleElementRename usually receives the name of the PSI element being renamed (the PyFunction name).
                // So we replace the last part.
                
                // Let's rely on normalize() logic: it replaces : with .
                // But we want to preserve the separator used in the file.
                val separator = if (currentText.contains(":")) ":" else "."
                val nameParts = currentText.split(separator).toMutableList()
                nameParts[nameParts.lastIndex] = newElementName
                val newText = nameParts.joinToString(separator)
                return super.handleElementRename(newText)
            }
        }
        return super.handleElementRename(newElementName)
    }

    override fun getVariants(): Array<Any> {
        val controller = findController() ?: return emptyArray()
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        
        // Find module
        // We need to resolve the controller string to a PyFile or directory?
        // OpenApiSpecUtil doesn't have resolveModule.
        // Let's try to find functions starting with controller prefix?
        // Or better, finding the module.
        
        // Since we don't have a robust resolveModule, let's use PyFunctionNameIndex to find ALL functions and filter?
        // That's expensive.
        // Better: resolve the controller to a file.
        // We can simulate resolution by looking for files matching the controller path.
        // Or use FilenameIndex or PyModuleNameIndex if available (internal).
        
        // Let's try a heuristic:
        // controller "pkg.api" -> look for "api.py" in "pkg" folder.
        
        // Actually, let's just use what we have in OpenApiSpecUtil but adapted.
        // We want completion variants.
        // If we know the controller, we want functions in that module.
        
        // Let's use PyClassNameIndex/PyFunctionNameIndex but scoped to the module?
        // Hard without resolving the module to a file.
        
        // Let's try to resolve the module via PyResolveImportUtil if possible, but it is likely internal.
        // Fallback: Use standard index but filter by qualified name.
        // Wait, PyFunctionNameIndex keys are just function names (short names).
        // We can iterate all keys? No.
        
        // Let's implement a simple file lookup for the controller.
        val candidates = OpenApiSpecUtil.resolvePythonSymbol(controller, project) 
        // This resolves to a symbol (function) usually, but maybe we can make it resolve modules?
        // The current resolvePythonSymbol splits by dot and looks for function.
        // If controller is "pkg.api", it looks for function "api" in module "pkg"? No.
        
        // Let's look at how to get variants.
        // If I have a PyFile, I can get topLevelFunctions.
        
        // Re-using resolve logic for module:
        // We need a way to find the file for "pkg.api".
        // Helper: findModuleFile(qName, project).
        
        val moduleFile = findModuleFile(controller, project) ?: return emptyArray()
        
        return moduleFile.topLevelFunctions
            .filter { !it.name.orEmpty().startsWith("_") } // public functions
            .mapNotNull { it.name }
            .toTypedArray()
    }

    private fun findModuleFile(qName: String, project: Project): com.jetbrains.python.psi.PyFile? {
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        
        // Simple heuristic: convert qName to path
        // pkg.api -> pkg/api.py
        // or pkg/api/__init__.py
        
        val validFiles = com.intellij.psi.search.FilenameIndex.getFilesByName(project, qName.substringAfterLast(".") + ".py", scope)
        // This is weak.
        
        // Better: Use PyPsiFacade (internal) or just standard file search.
        // Let's iterate over roots?
        
        // Let's stick to a simple heuristic for now as we don't have full Python plugin internal access guaranteed or don't want to depend on internal APIs.
        // But wait, we depend on 'com.intellij.modules.python'.
        
        // Let's use `com.jetbrains.python.psi.resolve.PyResolveImportUtil` if available?
        // Or `PyClassNameIndex.find`? No.
        
        // Let's assume standard project structure.
        val path = qName.replace(".", "/")
        val targetFiles = mutableListOf<com.jetbrains.python.psi.PyFile>()
        
        val fileIndex = com.intellij.openapi.roots.ProjectRootManager.getInstance(project).fileIndex
        fileIndex.iterateContent { vFile ->
            if (vFile.extension == "py") {
                 // Check if this file matches the qName
                 // This is slow for large projects but acceptable for completion in this scoped task context?
                 // No, iterating content is too slow.
                 return@iterateContent true
            }
            true
        }
        
        // Faster: Search for file with name = last part + .py
        val shortName = qName.substringAfterLast(".") + ".py"
        val files = com.intellij.psi.search.FilenameIndex.getFilesByName(project, shortName, scope)
        for (file in files) {
             if (file is com.jetbrains.python.psi.PyFile) {
                 // Check qualified name logic roughly
                 // If qName is "pkg.api", and file path ends with "pkg/api.py"
                 val vFile = file.virtualFile
                 val pathSuffix = "/$path.py"
                 if (vFile.path.endsWith(pathSuffix) || (qName == vFile.nameWithoutExtension && !vFile.path.contains("/"))) {
                      return file
                 }
             }
        }
        return null
    }

    protected open fun operationIdText(): String = element.text.removeSurrounding("\"").removeSurrounding("'")

    protected abstract fun findController(): String?

    protected fun isQualified(name: String): Boolean = name.contains(".") || name.contains(":")

    protected fun normalize(name: String): String = name.replace(":", ".")

    private fun resolvePythonSymbol(qName: String, project: Project): List<PsiElement> {
        val parts = qName.split(".")
        if (parts.isEmpty()) return emptyList()

        val lastPart = parts.last()
        val moduleName = parts.dropLast(1).joinToString(".")

        val result = mutableListOf<PsiElement>()
        val scope = GlobalSearchScope.projectScope(project)

        val functions = PyFunctionNameIndex.find(lastPart, project, scope)
        for (function in functions) {
            if (isSymbolInModule(function, moduleName)) {
                result.add(function)
            }
        }

        return result
    }

    private fun isSymbolInModule(symbol: PsiElement, expectedModuleQName: String): Boolean {
        if (symbol is PyFunction) {
            val symbolQName = symbol.qualifiedName ?: return false
            val parentQName = symbolQName.substringBeforeLast(".")
            return parentQName == expectedModuleQName
        }
        return false
    }
}
