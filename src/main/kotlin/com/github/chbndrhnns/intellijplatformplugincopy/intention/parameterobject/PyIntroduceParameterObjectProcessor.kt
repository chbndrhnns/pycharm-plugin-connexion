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
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex
import com.jetbrains.python.psi.types.TypeEvalContext

class PyIntroduceParameterObjectProcessor(
    private val function: PyFunction,
    private val configSelector: ((List<PyNamedParameter>, String) -> IntroduceParameterObjectSettings?)? = null
) {

    fun run() {
        val project = function.project

        val allParams = collectParameters(function)
        if (allParams.isEmpty()) return

        val defaultClassName = generateDataclassName(function)

        val settings = if (configSelector != null) {
            configSelector.invoke(allParams, defaultClassName)
        } else {
            val dialog = IntroduceParameterObjectDialog(project, allParams, defaultClassName)
            dialog.show()
            if (dialog.isOK) {
                dialog.getSettings()
            } else {
                return
            }
        }

        if (settings == null || settings.selectedParameters.isEmpty()) return

        val params = settings.selectedParameters
        val dataclassName = settings.className
        val parameterName = settings.parameterName

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

        WriteCommandAction.writeCommandAction(project, function.containingFile)
            .withName("Introduce Parameter Object")
            .run<Throwable> {
                val dataclass = createDataclass(
                    project,
                    function,
                    dataclassName,
                    params,
                    settings.generateFrozen,
                    settings.generateSlots,
                    settings.generateKwOnly
                )
            
            // Add imports
            addDataclassImport(function)
            
            // Update body
                updateFunctionBody(project, function, params, paramUsages, parameterName)
            
            // Update call sites
            updateCallSites(project, function, dataclass, params, functionUsages)

            // Update function signature
                replaceFunctionSignature(project, function, dataclassName, params, parameterName)
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
        val baseName = name.split('_')
            .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } } + "Params"

        val file = function.containingFile as? PyFile ?: return baseName

        var candidate = baseName
        var index = 1
        while (isNameTaken(file, candidate)) {
            candidate = baseName + index
            index++
        }
        return candidate
    }

    private fun isNameTaken(file: PyFile, name: String): Boolean {
        if (file.findTopLevelClass(name) != null) return true
        if (file.findTopLevelFunction(name) != null) return true
        if (file.findTopLevelAttribute(name) != null) return true

        for (stmt in file.importBlock) {
            for (element in stmt.importElements) {
                val visibleName = element.asName ?: element.importedQName?.lastComponent
                if (visibleName == name) return true
            }
        }
        return false
    }

    private fun createDataclass(
        project: Project,
        function: PyFunction,
        className: String,
        params: List<PyNamedParameter>,
        generateFrozen: Boolean,
        generateSlots: Boolean,
        generateKwOnly: Boolean
    ): PyClass {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(function)

        val sb = StringBuilder()
        val decoratorArgs = mutableListOf<String>()
        if (generateFrozen) decoratorArgs.add("frozen=True")
        if (generateSlots) decoratorArgs.add("slots=True")
        if (generateKwOnly) decoratorArgs.add("kw_only=True")

        if (decoratorArgs.isNotEmpty()) {
            sb.append("@dataclass(${decoratorArgs.joinToString(", ")})\n")
        } else {
            sb.append("@dataclass\n")
        }

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

        val containingClass = function.containingClass
        if (containingClass != null) {
            // Method in class -> Global scope (File level)
            var anchor: PsiElement = function
            while (anchor.parent != file && anchor.parent != null) {
                anchor = anchor.parent
            }
            val added = file.addBefore(newClass, anchor) as PyClass
            file.addBefore(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n\n"), anchor)
            return added
        } else {
            // Function (Top-level or Nested) -> Local scope (Same level)
            val parent = function.parent
            val added = parent.addBefore(newClass, function) as PyClass
            parent.addBefore(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n\n"), function)
            return added
        }
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
        params: List<PyNamedParameter>,
        parameterName: String
    ) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(function)

        val newParamText = "$parameterName: $dataclassName"
        val firstExtractedParam = params.first()

        val newParamsList = mutableListOf<String>()

        for (p in function.parameterList.parameters) {
            if (p in params) {
                if (p == firstExtractedParam) {
                    newParamsList.add(newParamText)
                }
            } else {
                newParamsList.add(p.text)
            }
        }

        // Cleanup dangling * or * before **kwargs
        val cleanedParams = mutableListOf<String>()
        for (i in newParamsList.indices) {
            val param = newParamsList[i]
            if (param == "*") {
                // Remove if last
                if (i == newParamsList.lastIndex) continue

                // Remove if next is **kwargs
                val next = newParamsList[i + 1]
                if (next.startsWith("**")) continue
            }
            cleanedParams.add(param)
        }

        val newSignature = "def foo(${cleanedParams.joinToString(", ")}): pass"
        val dummyFunc = generator.createFromText(languageLevel, PyFunction::class.java, newSignature)

        function.parameterList.replace(dummyFunc.parameterList)
        
        CodeStyleManager.getInstance(project).reformat(function.containingFile)
    }

    private fun updateFunctionBody(
        project: Project,
        function: PyFunction,
        params: List<PyNamedParameter>,
        paramUsages: Map<PyNamedParameter, Collection<PsiReference>>,
        parameterName: String
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
                    val newExpr = generator.createExpressionFromText(
                        LanguageLevel.forElement(function),
                        "$parameterName.$paramName"
                    )
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

        val allParams = function.parameterList.parameters.toList()
        if (allParams.isEmpty()) return

        // We assume the first extracted parameter defines the position of the new 'params' argument.
        val firstExtractedParam = params.firstOrNull() ?: return

        val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeAnalysis(project, function.containingFile))

        for (ref in functionUsages) {
            val element = ref.element
            val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: continue
            if (call.callee != element && call.callee?.reference?.resolve() != function) continue

            val args = call.argumentList?.arguments ?: continue

            val markedCallee = call.multiResolveCallee(resolveContext).firstOrNull()
            val implicitOffset = markedCallee?.implicitOffset ?: 0

            // Manual argument mapping
            val paramToArgs = mutableMapOf<PyParameter, MutableList<String>>()

            fun findParam(name: String): PyNamedParameter? {
                return allParams.filterIsInstance<PyNamedParameter>().find { it.name == name }
            }

            val kwargsParam = allParams.filterIsInstance<PyNamedParameter>().find { it.isKeywordContainer }
            val argsParam = allParams.filterIsInstance<PyNamedParameter>().find { it.isPositionalContainer }

            var positionalIndex = implicitOffset
            var seenStar = false

            for (arg in args) {
                if (arg is PyKeywordArgument) {
                    val keyword = arg.keyword
                    if (keyword != null) {
                        val param = findParam(keyword)
                        if (param != null && !param.isKeywordContainer && !param.isPositionalContainer) {
                            paramToArgs.computeIfAbsent(param) { mutableListOf() }
                                .add(arg.valueExpression?.text ?: "None")
                        } else {
                            if (kwargsParam != null) {
                                paramToArgs.computeIfAbsent(kwargsParam) { mutableListOf() }.add(arg.text)
                            }
                        }
                    }
                } else if (arg.text.startsWith("**")) {
                    if (kwargsParam != null) {
                        paramToArgs.computeIfAbsent(kwargsParam) { mutableListOf() }.add(arg.text)
                    }
                } else if (arg.text.startsWith("*")) {
                    // Treat *args expansion as positional if we have *args param
                    if (argsParam != null) {
                        paramToArgs.computeIfAbsent(argsParam) { mutableListOf() }.add(arg.text)
                    }
                } else {
                    // Positional
                    var mapped = false
                    while (positionalIndex < allParams.size) {
                        val p = allParams[positionalIndex]

                        if (p is PySingleStarParameter) {
                            seenStar = true
                            positionalIndex++
                            continue
                        }
                        if (p is PySlashParameter) {
                            positionalIndex++
                            continue
                        }

                        if (p is PyNamedParameter) {
                            if (p.isPositionalContainer) {
                                seenStar = true
                                paramToArgs.computeIfAbsent(p) { mutableListOf() }.add(arg.text)
                                break
                            }
                            if (p.isKeywordContainer) {
                                positionalIndex++
                                continue
                            }
                            if (seenStar) { // Keyword-only, skip
                                positionalIndex++
                                continue
                            }

                            paramToArgs.computeIfAbsent(p) { mutableListOf() }.add(arg.text)
                            positionalIndex++
                            break
                        }
                        positionalIndex++
                    }
                }
            }

            val newArgsList = StringBuilder()
            val dataclassArgsList = StringBuilder()

            dataclassArgsList.append("$dataclassName(")

            // Build Dataclass constructor arguments
            var firstDcArg = true
            for (p in params) {
                val argTexts = paramToArgs[p]
                if (argTexts != null && argTexts.isNotEmpty()) {
                    if (!firstDcArg) dataclassArgsList.append(", ")
                    dataclassArgsList.append("${p.name}=${argTexts.first()}")
                    firstDcArg = false
                }
            }
            dataclassArgsList.append(")")

            // Build new Function call arguments
            var firstFuncArg = true
            for (p in allParams) {
                if (p == firstExtractedParam) {
                    if (!firstFuncArg) newArgsList.append(", ")
                    newArgsList.append(dataclassArgsList)
                    firstFuncArg = false
                }

                if (p in params) {
                    // Extracted
                } else {
                    if (p is PySlashParameter || p is PySingleStarParameter) continue

                    val argTexts = paramToArgs[p]
                    if (argTexts != null) {
                        for (txt in argTexts) {
                            if (!firstFuncArg) newArgsList.append(", ")
                            newArgsList.append(txt)
                            firstFuncArg = false
                        }
                    }
                }
            }

            // Replace arguments
            val newArgExpr = try {
                val exprText = "f($newArgsList)"
                generator.createExpressionFromText(languageLevel, exprText).let {
                    (it as PyCallExpression).argumentList
                }
            } catch (e: Exception) {
                null
            }

            if (newArgExpr != null) {
                call.argumentList?.replace(newArgExpr)
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
