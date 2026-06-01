package com.github.chbndrhnns.connexion.features.connexion

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.jetbrains.python.psi.PyFile

abstract class ConnexionReferenceBase(element: PsiElement) : PsiPolyVariantReferenceBase<PsiElement>(element, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = element.project
        val operationId = operationIdText()
        if (operationId.isBlank()) return ResolveResult.EMPTY_ARRAY

        val candidates = candidateQualifiedNames()

        for (qName in candidates) {
            val resolved = OpenApiSpecUtil.resolvePythonSymbol(qName, project)
            if (resolved.isNotEmpty()) {
                return resolved.map { PsiElementResolveResult(it) }.toTypedArray()
            }
        }

        return ResolveResult.EMPTY_ARRAY
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
        val controller = findController()
        val project = element.project

        val operationModule = operationIdModulePrefix()
        val modulePath = when {
            controller != null && operationModule.isNotEmpty() -> "$controller.$operationModule"
            controller != null -> controller
            operationModule.isNotEmpty() -> operationModule
            else -> return emptyArray()
        }

        val item = OpenApiSpecUtil.resolvePath(project, normalize(modulePath))
        
        if (item is com.intellij.psi.PsiDirectory) {
            val dirs = item.subdirectories.mapNotNull { operationVariantText(operationModule, it.name) }
            val files = item.files
                .filter { it is PyFile && it.name != "__init__.py" }
                .mapNotNull { operationVariantText(operationModule, it.name.removeSuffix(".py")) }
            return (dirs + files).toTypedArray()
        }

        if (item is PyFile) {
            return item.topLevelFunctions
                .filter { !it.name.orEmpty().startsWith("_") }
                .mapNotNull { operationVariantText(operationModule, it.name) }
                .toTypedArray()
        }
        
        return emptyArray()
    }

    internal open fun operationIdText(): String = element.text.removeSurrounding("\"").removeSurrounding("'")

    internal abstract fun findController(): String?

    internal fun isQualified(name: String): Boolean = name.contains(".") || name.contains(":")

    internal fun controllerPathResolves(): Boolean {
        val controller = findController() ?: return true
        return OpenApiSpecUtil.resolvePath(element.project, normalize(controller)) != null
    }

    internal fun resolvesToModulePath(): Boolean {
        return candidateQualifiedNames().any { OpenApiSpecUtil.resolvePath(element.project, it) != null }
    }

    protected fun normalize(name: String): String = name.replace(":", ".")

    private fun candidateQualifiedNames(): List<String> {
        val operationId = operationIdText()
        if (operationId.isBlank()) return emptyList()

        val controller = findController()
        val candidates = mutableListOf<String>()
        if (controller != null) {
            candidates.add(normalize("$controller.$operationId"))
        }

        if (isQualified(operationId) || controller == null) {
            val operationQName = normalize(operationId)
            if (!candidates.contains(operationQName)) {
                candidates.add(operationQName)
            }
        }
        return candidates
    }

    private fun operationIdModulePrefix(): String {
        val operationId = operationIdText()
            .replace("IntellijIdeaRulezzz", "")
            .trimEnd()

        return when {
            operationId.contains(":") -> operationId.substringBeforeLast(":")
            operationId.contains(".") -> operationId.substringBeforeLast(".")
            else -> ""
        }
    }

    private fun operationVariantText(modulePrefix: String, functionName: String?): String? {
        if (functionName == null) return null
        if (modulePrefix.isEmpty()) return functionName

        val separator = if (operationIdText().contains(":")) ":" else "."
        return "$modulePrefix$separator$functionName"
    }
}
