package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class PyIntroduceParameterObjectProcessor(private val function: PyFunction) {

    fun run() {
        val project = function.project

        val params = collectParameters(function)
        if (params.isEmpty()) return

        val dataclassName = generateDataclassName(function)

        var paramUsages: Map<PyNamedParameter, Collection<PsiReference>> = emptyMap()
        var functionUsages: Collection<PsiReference> = emptyList()

        runWithModalProgressBlocking(project, "Searching for usages...") {
            readAction {
                val pUsages = mutableMapOf<PyNamedParameter, Collection<PsiReference>>()
                for (p in params) {
                    if (p.name != null) {
                        pUsages[p] =
                            ReferencesSearch.search(p, GlobalSearchScope.fileScope(function.containingFile)).findAll()
                    }
                }
                paramUsages = pUsages
                functionUsages = ReferencesSearch.search(function, GlobalSearchScope.projectScope(project)).findAll()
            }
        }

        WriteCommandAction.writeCommandAction(project, function.containingFile).run<Throwable> {
            val dataclass = createDataclass(project, function, dataclassName, params)
            
            // Add imports
            addDataclassImport(function)
            
            // Update body
            updateFunctionBody(project, function, dataclassName, params, paramUsages)
            
            // Update call sites
            updateCallSites(project, function, dataclassName, params, functionUsages)

            // Update function signature
            replaceFunctionSignature(project, function, dataclassName, params)
        }
    }

    private fun collectParameters(function: PyFunction): List<PyNamedParameter> {
        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }

        val isClassMethod = function.decoratorList?.findDecorator("classmethod") != null
        if (isClassMethod && parameters.isNotEmpty() && parameters.first().name == "cls") {
             return parameters.drop(1)
        }
        return parameters
    }

    private fun generateDataclassName(function: PyFunction): String {
        val name = function.name ?: "Params"
        // simple snake_case to CamelCase conversion for MVP
        return name.split('_')
            .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } } + "Params"
    }

    private fun createDataclass(
        project: Project,
        function: PyFunction,
        className: String,
        params: List<PyNamedParameter>
    ): PyClass {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(function)

        val sb = StringBuilder()
        sb.append("@dataclass\n")
        sb.append("class $className:\n")
        for (p in params) {
            val ann = p.annotationValue
            val typeText = ann ?: "Any"
            sb.append("    ${p.name}: $typeText")
            if (p.hasDefaultValue()) {
                sb.append(" = ${p.defaultValueText}")
            }
            sb.append("\n")
        }

        val file = function.containingFile as PyFile
        val newClass = generator.createFromText(languageLevel, PyClass::class.java, sb.toString())
        
        // Insert before function's top-level container
        var anchor: PsiElement = function
        while (anchor.parent != file && anchor.parent != null) {
            anchor = anchor.parent
        }

        val added = file.addBefore(newClass, anchor) as PyClass
        file.addBefore(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n\n"), anchor)
        
        return added
    }

    private fun addDataclassImport(function: PyFunction) {
        // Simplistic import addition for MVP
        val file = function.containingFile as PyFile
        val generator = PyElementGenerator.getInstance(function.project)
        val languageLevel = LanguageLevel.forElement(function)
        val whitespace = PsiParserFacade.getInstance(function.project).createWhiteSpaceFromText("\n\n")
        val importWhitespace = PsiParserFacade.getInstance(function.project).createWhiteSpaceFromText("\n")
        
        // Check if already imported
        // This is a bit manual, real plugin would use PyImportService or similar.
        // "MVP: add from dataclasses import dataclass if not present."
        
        // Also need 'Any' if we used it
        if (!file.text.contains("from typing import Any")) { // Very naive check
             val importStmt = generator.createFromText(languageLevel, PyFromImportStatement::class.java, "from typing import Any")
             val first = file.firstChild
             val addedImport = file.addBefore(importStmt, first)
             file.addAfter(importWhitespace, addedImport)
        }

        if (!file.text.contains("from dataclasses import dataclass")) {
            val importStmt = generator.createFromText(languageLevel, PyFromImportStatement::class.java, "from dataclasses import dataclass")
            if (file.importBlock.isNotEmpty()) {
                file.addBefore(importStmt, file.importBlock.first())
                file.addBefore(importWhitespace, file.importBlock.first())
            } else {
                val first = file.firstChild
                val addedImport = file.addBefore(importStmt, first)
                file.addAfter(importWhitespace, addedImport)
            }
        }
    }

    private fun replaceFunctionSignature(
        project: Project,
        function: PyFunction,
        dataclassName: String,
        params: List<PyNamedParameter>
    ) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(function)
        
        // Create new parameter
        val newParam = generator.createParameter("params", null, dataclassName, languageLevel)
        
        // Insert new parameter at the position of the first removed parameter
        val firstParam = params.first()
        function.parameterList.addBefore(newParam, firstParam)
        
        // Remove old parameters
        for (p in params) {
            p.delete()
        }
        
        // Clean up commas and spaces
        CodeStyleManager.getInstance(project).reformat(function.containingFile)
    }

    private fun updateFunctionBody(
        project: Project,
        function: PyFunction,
        dataclassName: String,
        params: List<PyNamedParameter>,
        paramUsages: Map<PyNamedParameter, Collection<PsiReference>>
    ) {
        val generator = PyElementGenerator.getInstance(project)
        
        // Find usages of parameters inside function body
        for (p in params) {
            val paramName = p.name ?: continue
            val references = paramUsages[p] ?: continue
            
            for (ref in references) {
                val element = ref.element
                // Verify it's inside the function
                if (element.isValid && PsiTreeUtil.isAncestor(function, element, true)) {
                    val newExpr = generator.createExpressionFromText(LanguageLevel.forElement(function), "params.$paramName")
                    element.replace(newExpr)
                }
            }
        }
    }

    private fun updateCallSites(
        project: Project,
        function: PyFunction,
        dataclassName: String,
        params: List<PyNamedParameter>,
        functionUsages: Collection<PsiReference>
    ) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(function)

        for (ref in functionUsages) {
            val element = ref.element
            val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: continue
            if (call.callee != element && call.callee?.reference?.resolve() != function) continue
            
            val args = call.argumentList?.arguments ?: continue
            
            // For MVP we assume positional only and order matches.
            // "MVP: only allow simple positional arguments."
            
            // Build new argument string: Dataclass(arg1, arg2...)
            val newArgs = StringBuilder()
            newArgs.append("$dataclassName(")
            
            val paramNames = params.map { it.name }
            
            // We need to map arguments to parameters.
            // Since we filtered out self/cls, the params list matches the arguments 5.1 example style (simple function).
            // If it's a method, implicit self is handled by call.
            
            // Map params indices to args.
            // Problem: The call might have other arguments if we only selected a subset?
            // But MVP selects ALL parameters except self.
            
            // So we replace ALL arguments with the dataclass instantiation?
            // If 'self' is not in args (it is implicit), then args match params.
            
            // Wait, check if function is method.
            // If method, call.arguments usually includes `self` if called as Class.method(self, ...)?
            // No, `PyCallExpression.getArguments()` returns explicit arguments.
            
            // We iterate over params and pick corresponding args.
            // If args count matches params count (assuming no defaults for MVP).
            
            if (args.size > params.size) {
                // Skipping complex cases for MVP
                continue 
            }
            
            for ((i, arg) in args.withIndex()) {
                if (i > 0) newArgs.append(", ")
                newArgs.append(arg.text)
            }
            newArgs.append(")")
            
            val newArgExpr = generator.createExpressionFromText(languageLevel, newArgs.toString())
            
            // Replace arguments
            // We want to replace the whole list of arguments with one argument.
            // Simplest: remove all args, add new one.
            
            val argList = call.argumentList
            if (argList != null) {
                for (arg in args) {
                    arg.delete()
                }
                argList.addArgument(newArgExpr as PyExpression)
            }

            // Handle cross-file import
            val usageFile = element.containingFile
            if (usageFile != function.containingFile && usageFile is PyFile) {
                addImportToUsageFile(usageFile, function.containingFile.name, dataclassName)
            }
        }
    }

    private fun addImportToUsageFile(file: PyFile, sourceFileName: String, dataclassName: String) {
        val moduleName = sourceFileName.removeSuffix(".py")
        val importText = "from $moduleName import $dataclassName"

        if (!file.text.contains(importText)) {
            val generator = PyElementGenerator.getInstance(file.project)
            val languageLevel = LanguageLevel.forElement(file)
            val importStmt = generator.createFromText(languageLevel, PyFromImportStatement::class.java, importText)
            val whitespace = PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("\n")

            if (file.importBlock.isNotEmpty()) {
                file.addBefore(importStmt, file.importBlock.first())
                file.addBefore(whitespace, file.importBlock.first())
            } else {
                val first = file.firstChild
                val added = file.addBefore(importStmt, first)
                file.addAfter(whitespace, added)
            }
        }
    }
}
