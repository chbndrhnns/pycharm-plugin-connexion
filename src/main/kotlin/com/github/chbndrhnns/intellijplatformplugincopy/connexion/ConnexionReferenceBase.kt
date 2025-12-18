package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult

abstract class ConnexionReferenceBase(element: PsiElement) : PsiPolyVariantReferenceBase<PsiElement>(element, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = element.project
        val operationId = operationIdText()
        if (operationId.isBlank()) return ResolveResult.EMPTY_ARRAY

        val controller = findController()

        val candidates = mutableSetOf<String>()
        if (controller != null) {
            candidates.add(normalize("$controller.$operationId"))
        }

        if (isQualified(operationId) || controller == null) {
            candidates.add(normalize(operationId))
        }

        if (candidates.isEmpty()) return ResolveResult.EMPTY_ARRAY

        val results = mutableListOf<ResolveResult>()
        for (qName in candidates) {
            val resolved = OpenApiSpecUtil.resolvePythonSymbol(qName, project)
            results.addAll(resolved.map { PsiElementResolveResult(it) })
        }

        return results.toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val currentText = operationIdText()
        if (isQualified(currentText)) {
            val separator = if (currentText.contains(":")) ":" else "."
            val nameParts = currentText.split(separator).toMutableList()
            nameParts[nameParts.lastIndex] = newElementName
            val newText = nameParts.joinToString(separator)
            return super.handleElementRename(newText)
        }
        return super.handleElementRename(newElementName)
    }

    override fun getVariants(): Array<Any> {
        val controller = findController() ?: return emptyArray()
        val project = element.project
        
        val item = OpenApiSpecUtil.resolvePath(project, controller)
        
        if (item is com.jetbrains.python.psi.PyFile) {
             return item.topLevelFunctions
                .filter { !it.name.orEmpty().startsWith("_") }
                .mapNotNull { it.name }
                .toTypedArray()
        }
        
        return emptyArray()
    }

    protected open fun operationIdText(): String = element.text.removeSurrounding("\"").removeSurrounding("'")

    protected abstract fun findController(): String?

    protected fun isQualified(name: String): Boolean = name.contains(".") || name.contains(":")

    protected fun normalize(name: String): String = name.replace(":", ".")
}
