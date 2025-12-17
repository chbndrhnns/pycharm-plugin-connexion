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
