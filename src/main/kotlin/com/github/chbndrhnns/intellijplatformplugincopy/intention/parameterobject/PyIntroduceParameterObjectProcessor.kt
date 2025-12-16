package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil

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
        var callSiteUpdates: List<CallSiteUpdateInfo> = emptyList()

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
                val functionUsages =
                    ReferencesSearch.search(function, GlobalSearchScope.projectScope(project)).findAll()

                callSiteUpdates = prepareCallSiteUpdates(
                    project,
                    function,
                    dataclassName,
                    params,
                    parameterName,
                    functionUsages
                )
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

                addDataclassImport(function)
                updateFunctionBody(project, function, params, paramUsages, parameterName)
                applyCallSiteUpdates(project, function, dataclass, callSiteUpdates)
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
        val rawBaseName = name.split('_')
            .filter { it.isNotBlank() }
            .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } }

        val baseName = ensureValidPythonIdentifier((rawBaseName.ifBlank { "Params" }) + "Params")

        val file = function.containingFile as? PyFile ?: return baseName

        var candidate = baseName
        var index = 1
        while (isNameTaken(file, candidate)) {
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

        // 1. Basic class shell generation (class ClassName: pass)
        val pyClass = generator.createFromText(languageLevel, PyClass::class.java, "class $className:\n")

        // 2. @dataclass decorator creation and addition
        val decoratorArgs = mutableListOf<String>()
        if (generateFrozen) decoratorArgs.add("frozen=True")
        if (generateSlots) decoratorArgs.add("slots=True")
        if (generateKwOnly) decoratorArgs.add("kw_only=True")

        val decoratorText = if (decoratorArgs.isNotEmpty()) {
            "@dataclass(${decoratorArgs.joinToString(", ")})"
        } else {
            "@dataclass"
        }

        // Decorator list creation
        val decoratorList = generator.createDecoratorList(decoratorText)

        // Add decorator before class definition ('class' keyword)
        // pyClass.firstChild is usually the 'class' keyword.
        val classKeyword = pyClass.firstChild
        pyClass.addBefore(decoratorList, classKeyword)
        // Add a newline between the decorator and the class definition
        pyClass.addBefore(generator.createNewLine(), classKeyword)

        // 3. Add Attribute (Field)
        val statementList = pyClass.statementList
        // Remove the initially generated 'pass' statement
        statementList.statements.firstOrNull()?.delete()

        for (p in params) {
            val ann = p.annotationValue
            val typeText = ann ?: "Any"

            // Text generation for each field (e.g., name: str = "default")
            val fieldText = StringBuilder().apply {
                append(p.name)
                append(": ")
                append(typeText)
                if (p.defaultValue != null) {
                    append(" = ")
                    append(p.defaultValueText)
                }
            }.toString()

            // Statement PSI Element Creation and Addition to Class Body
            val fieldStatement = generator.createFromText(languageLevel, PyStatement::class.java, fieldText)
            statementList.add(fieldStatement)
        }

        // 4. Insert the generated class into the actual PSI tree
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

        // 5. Run code formatting (automatic indentation and style cleanup)
        return CodeStyleManager.getInstance(project).reformat(addedClass) as PyClass
    }
    private fun addDataclassImport(function: PyFunction) {
        val file = function.containingFile as? PyFile ?: return

        AddImportHelper.addOrUpdateFromImportStatement(
            file, "typing", "Any", null, AddImportHelper.ImportPriority.BUILTIN, function
        )

        AddImportHelper.addOrUpdateFromImportStatement(
            file, "dataclasses", "dataclass", null, AddImportHelper.ImportPriority.BUILTIN, function
        )
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

                    val languageLevel = LanguageLevel.forElement(element)
                    val newExprText = "$parameterName.$paramName"
                    val newExpr = generator.createExpressionFromText(languageLevel, newExprText)

                    // Handle precedence: Wrap in parentheses if the new expression
                    // has lower precedence than the context requires.
                    if (PyReplaceExpressionUtil.isNeedParenthesis(element, newExpr)) {
                        val parenthesized = generator.createExpressionFromText(languageLevel, "($newExprText)")
                        element.replace(parenthesized)
                    } else {
                        element.replace(newExpr)
                    }
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
        functionUsages: Collection<PsiReference>
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

            val newArgListElement = try {
                generator.createArgumentList(languageLevel, "($newArgsText)")
            } catch (e: Exception) {
                LOG.debug("Failed to create argument list from '$newArgsText'", e)
                null
            }

            if (newArgListElement != null) {
                call.argumentList?.replace(newArgListElement)
            }

            if (updateInfo.needsDataclassImport) {
                val usageFile = call.containingFile
                if (usageFile is PyFile) {
                    AddImportHelper.addImport(dataclass, usageFile, call)
                }
            }
        }
    }
}
