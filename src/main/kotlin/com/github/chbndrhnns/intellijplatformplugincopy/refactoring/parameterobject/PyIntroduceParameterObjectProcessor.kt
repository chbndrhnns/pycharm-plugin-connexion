package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.generator.ParameterObjectGeneratorFactory
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.concurrent.CancellationException

private val LOG = logger<PyIntroduceParameterObjectProcessor>()

class PyIntroduceParameterObjectProcessor(
    private val function: PyFunction,
    private val configSelector: ((List<PyNamedParameter>, String) -> IntroduceParameterObjectSettings?)? = null
) {

    private data class CallSiteUpdateInfo(
        val callExpression: PyCallExpression,
        val newArgumentListText: String,
        val needsDataclassImport: Boolean
    )

    fun run() {
        val project = function.project
        val rootFunction = findRootFunction(function)
        LOG.debug("Starting Introduce Parameter Object refactoring for function: ${function.name} (root: ${rootFunction.name})")

        val allParams = collectParameters(rootFunction)
        if (allParams.isEmpty()) {
            LOG.debug("No parameters found for function: ${rootFunction.name}")
            return
        }

        val defaultClassName = generateDataclassName(rootFunction)

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

        if (settings == null || settings.selectedParameters.isEmpty()) {
            LOG.debug("Refactoring cancelled or no parameters selected")
            return
        }

        val rootParams = settings.selectedParameters
        val dataclassName = settings.className
        val parameterName = settings.parameterName

        var functionsToUpdate: List<PyFunction> = emptyList()
        var paramUsages: Map<PyNamedParameter, Collection<PsiReference>> = emptyMap()
        var callSiteUpdates: List<CallSiteUpdateInfo> = emptyList()
        var filesToUpdate: List<PsiFile> = emptyList()

        try {
            runWithModalProgressBlocking(project, "Searching for usages...") {
                readAction {
                    // 1. Find all functions to update (root + overrides)
                    val overrides = PyOverridingMethodsSearch.search(rootFunction, true).findAll()
                    functionsToUpdate = listOf(rootFunction) + overrides
                    filesToUpdate = functionsToUpdate.map { it.containingFile }.distinct()

                    // 2. Collect param usages and call site updates for EACH function
                    val combinedParamUsages = mutableMapOf<PyNamedParameter, Collection<PsiReference>>()
                    val combinedCallSiteUpdates = mutableListOf<CallSiteUpdateInfo>()

                    val functionParamsMap = mutableMapOf<PyFunction, List<PyNamedParameter>>()

                    for (func in functionsToUpdate) {
                        val funcParams = collectParameters(func)
                        val paramsToUpdateInFunc = mutableListOf<PyNamedParameter>()

                        // Map root parameters to this function's parameters by name
                        for (rootParam in rootParams) {
                            val pName = rootParam.name
                            if (pName != null) {
                                val matchingParam = funcParams.find { it.name == pName }
                                if (matchingParam != null) {
                                    paramsToUpdateInFunc.add(matchingParam)

                                    val usages = ReferencesSearch.search(
                                        matchingParam,
                                        GlobalSearchScope.fileScope(func.containingFile)
                                    ).findAll()
                                    combinedParamUsages[matchingParam] = usages
                                }
                            }
                        }
                        functionParamsMap[func] = paramsToUpdateInFunc
                    }

                    for (func in functionsToUpdate) {
                        val paramsToUpdateInFunc = functionParamsMap[func] ?: emptyList()
                        val functionUsages =
                            ReferencesSearch.search(func, GlobalSearchScope.projectScope(project)).findAll()

                        combinedCallSiteUpdates.addAll(
                            prepareCallSiteUpdates(
                                project,
                                func,
                                dataclassName,
                                paramsToUpdateInFunc,
                                parameterName,
                                functionUsages,
                                functionParamsMap
                            )
                        )
                    }
                    paramUsages = combinedParamUsages
                    callSiteUpdates = combinedCallSiteUpdates
                }
            }
        } catch (e: Exception) {
            if (e is ProcessCanceledException || e is CancellationException) {
                LOG.debug("Refactoring process canceled")
                return
            }
            throw e
        }

        WriteCommandAction.writeCommandAction(project, *filesToUpdate.toTypedArray())
            .withName("Introduce Parameter Object")
            .run<Throwable> {
                val generator = ParameterObjectGeneratorFactory.getGenerator(settings.baseType)
                val languageLevel = LanguageLevel.forElement(rootFunction)

                val generatedClass = generator.generateClass(
                    project,
                    languageLevel,
                    dataclassName,
                    rootParams,
                    settings.generateFrozen,
                    settings.generateSlots,
                    settings.generateKwOnly
                )

                val dataclass = insertClassIntoFile(project, rootFunction, generatedClass)

                // Add required imports for the selected base type
                val file = rootFunction.containingFile as? PyFile
                if (file != null) {
                    generator.addRequiredImports(file, rootFunction)
                }

                // Update Signature and Body for ALL functions
                for (func in functionsToUpdate) {
                    val funcParams = collectParameters(func)
                    val paramsToUpdateInFunc = rootParams.mapNotNull { rp -> funcParams.find { it.name == rp.name } }

                    if (paramsToUpdateInFunc.isNotEmpty()) {
                        // Add import if in different file
                        if (func.containingFile != rootFunction.containingFile) {
                            val funcFile = func.containingFile as? PyFile
                            if (funcFile != null) {
                                AddImportHelper.addImport(dataclass, funcFile, func)
                            }
                        }

                        updateFunctionBody(
                            project,
                            func,
                            paramsToUpdateInFunc,
                            paramUsages,
                            parameterName,
                            settings.baseType == ParameterObjectBaseType.TYPED_DICT
                        )
                        replaceFunctionSignature(project, func, dataclassName, paramsToUpdateInFunc, parameterName)
                    }
                }

                applyCallSiteUpdates(project, rootFunction, dataclass, callSiteUpdates)
            }
    }

    private fun findRootFunction(function: PyFunction): PyFunction {
        val containingClass = function.containingClass ?: return function
        val name = function.name ?: return function
        val context = TypeEvalContext.codeInsightFallback(function.project)
        val superClasses = containingClass.getSuperClasses(context)

        var root = function
        for (cls in superClasses) {
            val method = cls.findMethodByName(name, false, context)
            if (method != null) {
                root = method
            }
        }
        return root
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
        val rawBaseName = name.split('_')
            .filter { it.isNotBlank() }
            .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } }

        val baseName = ensureValidPythonIdentifier((rawBaseName.ifBlank { "Params" }) + "Params")

        val file = function.containingFile as? PyFile ?: return baseName

        var candidate = baseName
        var index = 1
        while (ParameterObjectUtils.isNameTaken(file, candidate)) {
            candidate = baseName + index
            index++
        }
        return candidate
    }

    private fun ensureValidPythonIdentifier(name: String): String {
        if (name.isBlank()) return "Params"

        val sanitized = buildString {
            for (ch in name) {
                append(
                    when {
                        ch == '_' || ch.isLetterOrDigit() -> ch
                        else -> '_'
                    }
                )
            }
        }

        val first = sanitized.firstOrNull() ?: return "Params"
        return if (first == '_' || first.isLetter()) sanitized else "_$sanitized"
    }

    private fun insertClassIntoFile(
        project: Project,
        function: PyFunction,
        pyClass: PyClass
    ): PyClass {
        val file = function.containingFile
        val containingClass = function.containingClass

        // Determine Insertion Position (Anchor)
        val anchor: PsiElement = if (containingClass != null) {
            // If it is an in-class method: move to file level (Global scope)
            var temp: PsiElement = function
            while (temp.parent != file && temp.parent != null) {
                temp = temp.parent
            }
            temp
        } else {
            // If it is a top-level or nested function: same level (Local scope)
            function
        }

        val parent = anchor.parent
        val addedClass = parent.addBefore(pyClass, anchor) as PyClass

        // Add blank line before class definition
        parent.addBefore(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n\n"), anchor)

        // Run code formatting (automatic indentation and style cleanup)
        return CodeStyleManager.getInstance(project).reformat(addedClass) as PyClass
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

        // Create the new parameter: "parameterName: dataclassName"
        val newParam = generator.createParameter(parameterName, null, dataclassName, languageLevel)

        val firstParamToReplace = params.firstOrNull()

        // 1. Replace the first occurrence and delete the others
        // We iterate over the function's actual parameters to maintain order
        for (param in function.parameterList.parameters) {
            if (param in params) {
                if (param == firstParamToReplace) {
                    param.replace(newParam)
                } else {
                    param.delete()
                }
            }
        }

        // 2. Cleanup dangling * or * before **kwargs
        // Fetch the parameters again as the PSI tree has been modified
        val currentParams = function.parameterList.parameters
        for (i in currentParams.indices) {
            val param = currentParams[i]

            // Check for independent '*' parameter (PySingleStarParameter)
            if (param is PySingleStarParameter) {
                val isLast = i == currentParams.lastIndex

                // Check if the next parameter is **kwargs
                val nextIsKwArgs = if (!isLast) {
                    val next = currentParams[i + 1]
                    next is PyNamedParameter && next.isKeywordContainer
                } else {
                    false
                }

                if (isLast || nextIsKwArgs) {
                    param.delete()
                }
            }
        }
        // Commit pending PSI changes before reformatting to avoid document lock issues
        val document = function.containingFile.viewProvider.document
        if (document != null) {
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        }
        CodeStyleManager.getInstance(project).reformat(function.containingFile)
    }

    private fun updateFunctionBody(
        project: Project,
        function: PyFunction,
        params: List<PyNamedParameter>,
        paramUsages: Map<PyNamedParameter, Collection<PsiReference>>,
        parameterName: String,
        isTypedDict: Boolean = false
    ) {
        val generator = PyElementGenerator.getInstance(project)

        for (p in params) {
            val paramName = p.name ?: continue
            val references = paramUsages[p] ?: continue

            for (ref in references) {
                val element = ref.element

                // Ensure we are replacing code (reference expression), not docstrings or comments
                if (element is PyReferenceExpression &&
                    element.isValid &&
                    PsiTreeUtil.isAncestor(function, element, true)
                ) {

                    val newExprText = if (isTypedDict) {
                        if (p.defaultValue != null) {
                            "$parameterName.get(\"$paramName\", ${p.defaultValueText})"
                        } else {
                            "$parameterName[\"$paramName\"]"
                        }
                    } else {
                        "$parameterName.$paramName"
                    }

                    ParameterObjectUtils.replaceExpression(generator, element, newExprText)
                }
            }
        }
    }

    private fun prepareCallSiteUpdates(
        project: Project,
        function: PyFunction,
        dataclassName: String,
        params: List<PyNamedParameter>,
        parameterName: String,
        functionUsages: Collection<PsiReference>,
        functionParamsMap: Map<PyFunction, List<PyNamedParameter>>
    ): List<CallSiteUpdateInfo> {
        val result = mutableListOf<CallSiteUpdateInfo>()

        val allParams = function.parameterList.parameters.toList()
        if (allParams.isEmpty()) return emptyList()

        val firstExtractedParam = params.firstOrNull() ?: return emptyList()

        fun isKeywordOnlyParameter(param: PyParameter, allParams: List<PyParameter>): Boolean {
            val idx = allParams.indexOf(param)
            if (idx <= 0) return false

            // Everything after an explicit '*' or a '*args' is keyword-only.
            return allParams.subList(0, idx).any {
                it is PySingleStarParameter || (it is PyNamedParameter && it.isPositionalContainer)
            }
        }

        val resolveContext = PyResolveContext.defaultContext(
            TypeEvalContext.codeAnalysis(project, function.containingFile)
        )

        for (ref in functionUsages) {
            val element = ref.element
            val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: continue
            if (call.callee != element && call.callee?.reference?.resolve() != function) continue

            val mapping = call.multiMapArguments(resolveContext).firstOrNull {
                it.callableType?.callable == function
            } ?: continue

            val paramToArgs = mapping.mappedParameters.entries
                .groupBy({ it.value.parameter }, { it.key })

            val newArgsList = StringBuilder()
            val dataclassArgsList = StringBuilder()

            dataclassArgsList.append("$dataclassName(")

            var firstDcArg = true
            for (p in params) {
                val args = paramToArgs[p]
                if (!args.isNullOrEmpty()) {
                    val argExpr = args.first()

                    val argText = if (argExpr is PyKeywordArgument) {
                        argExpr.valueExpression?.text ?: "None"
                    } else if (argExpr is PyReferenceExpression) {
                        // Check if this reference resolves to a parameter that is being extracted from the containing function
                        val resolved = argExpr.reference.resolve()
                        if (resolved is PyNamedParameter) {
                            val containingFunction = PsiTreeUtil.getParentOfType(resolved, PyFunction::class.java)
                            val extractedParams = functionParamsMap[containingFunction]
                            if (extractedParams != null && resolved in extractedParams) {
                                "$parameterName.${resolved.name}"
                            } else {
                                argExpr.text
                            }
                        } else {
                            argExpr.text
                        }
                    } else {
                        argExpr.text
                    }

                    if (!firstDcArg) dataclassArgsList.append(", ")
                    dataclassArgsList.append("${p.name}=$argText")
                    firstDcArg = false
                }
            }
            dataclassArgsList.append(")")

            var firstFuncArg = true
            for (p in allParams) {
                if (p == firstExtractedParam) {
                    if (!firstFuncArg) newArgsList.append(", ")

                    if (isKeywordOnlyParameter(firstExtractedParam, allParams)) {
                        newArgsList.append("$parameterName=")
                    }
                    newArgsList.append(dataclassArgsList)
                    firstFuncArg = false
                }

                if (p in params) {
                    continue
                } else {
                    if (p is PySlashParameter || p is PySingleStarParameter) continue

                    val args = paramToArgs[p]
                    if (args != null) {
                        for (arg in args) {
                            if (!firstFuncArg) newArgsList.append(", ")
                            newArgsList.append(arg.text)
                            firstFuncArg = false
                        }
                    }
                }
            }

            val usageFile = element.containingFile
            val needsImport = usageFile != function.containingFile && usageFile is PyFile && element is PyElement

            result.add(
                CallSiteUpdateInfo(
                    callExpression = call,
                    newArgumentListText = newArgsList.toString(),
                    needsDataclassImport = needsImport
                )
            )
        }

        return result
    }

    private fun applyCallSiteUpdates(
        project: Project,
        function: PyFunction,
        dataclass: PyClass,
        callSiteUpdates: List<CallSiteUpdateInfo>
    ) {
        if (callSiteUpdates.isEmpty()) return

        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(function)

        for (updateInfo in callSiteUpdates) {
            val call = updateInfo.callExpression
            val newArgsText = updateInfo.newArgumentListText

            ParameterObjectUtils.replaceArgumentList(generator, languageLevel, call, newArgsText, LOG)

            if (updateInfo.needsDataclassImport) {
                val usageFile = call.containingFile
                if (usageFile is PyFile) {
                    AddImportHelper.addImport(dataclass, usageFile, call)
                }
            }
        }
    }
}
