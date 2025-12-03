package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.PyImportService
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
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex

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
            updateFunctionBody(project, function, params, paramUsages)
            
            // Update call sites
            updateCallSites(project, function, dataclass, params, functionUsages)

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
        val file = function.containingFile as? PyFile ?: return
        val project = function.project
        val importService = PyImportService()

        // Resolve and import 'typing.Any'
        val anyClass = PyPsiFacade.getInstance(project).createClassByQName("typing.Any", function)
        if (anyClass != null) {
            importService.ensureImportedIfNeeded(file, function, anyClass)
        }

        // Resolve and import 'dataclasses.dataclass'
        val dataclassClass = PyPsiFacade.getInstance(project).createClassByQName("dataclasses.dataclass", function)
        if (dataclassClass != null) {
            importService.ensureImportedIfNeeded(file, function, dataclassClass)
        } else {
            // Fallback if stubbed as function
            val dataclassFuncs = PyFunctionNameIndex.find("dataclass", project, GlobalSearchScope.allScope(project))
            val dataclassFunc = dataclassFuncs.firstOrNull {
                val name = it.containingFile.name
                name == "dataclasses.py" || name == "dataclasses.pyi"
            }
            if (dataclassFunc != null) {
                importService.ensureImportedIfNeeded(file, function, dataclassFunc)
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
        dataclass: PyClass,
        params: List<PyNamedParameter>,
        functionUsages: Collection<PsiReference>
    ) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(function)
        val dataclassName = dataclass.name ?: return

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

            val argList = call.argumentList
            if (argList != null) {
                for (arg in args) {
                    arg.delete()
                }
                argList.addArgument(newArgExpr)
            }

            // Handle cross-file import
            val usageFile = element.containingFile
            if (usageFile != function.containingFile && usageFile is PyFile && element is PyTypedElement) {
                addImportToUsageFile(usageFile, element, dataclass)
            }
        }
    }

    private fun addImportToUsageFile(file: PyFile, anchor: PyTypedElement, dataclass: PyClass) {
        PyImportService().ensureImportedIfNeeded(file, anchor, dataclass)
    }
}
