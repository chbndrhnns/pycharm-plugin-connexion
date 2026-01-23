package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.github.chbndrhnns.betterpy.core.MyBundle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext

class PyInlineParameterObjectProcessor(
    private val function: PyFunction,
    @Suppress("unused") private val invocationElement: PsiElement,
) {

    // Cache for expensive operations to share between countUsages() and run()
    private var cachedParameter: PyNamedParameter? = null
    private var cachedParameterObjectClass: PyClass? = null
    private var cachedClassUsages: Collection<PsiReference>? = null

    data class FieldInfo(
        val name: String,
        val typeText: String?,
        val defaultText: String?,
    )

    private data class Plan(
        val project: Project,
        val function: PyFunction,
        val parameter: PyNamedParameter,
        val parameterName: String,
        val parameterObjectClass: PyClass,
        val fields: List<FieldInfo>,
        val functionUsages: Collection<PsiReference>,
        val callSiteUpdates: List<CallSiteUpdateInfo>,
        val isTypedDict: Boolean,
        val consumedElements: List<PsiElement>
    )

    private data class CallSiteUpdateInfo(
        val callExpression: PyCallExpression,
        val newArgumentListText: String,
    )

    fun countUsages(): Int {
        val project = function.project
        val context = TypeEvalContext.codeAnalysis(project, function.containingFile)

        // Find the parameter object class
        val parameter = findInlineableParameter(function, context)
        if (parameter == null) {
            LOG.debug("countUsages: No inlineable parameter found for function ${function.name}")
            return 0
        }
        cachedParameter = parameter

        val parameterObjectClass = resolveParameterClass(parameter, context)
        if (parameterObjectClass == null) {
            LOG.debug("countUsages: Could not resolve parameter class for parameter ${parameter.name}")
            return 0
        }
        cachedParameterObjectClass = parameterObjectClass

        // Search for all references to the parameter object class
        val classReferences =
            ReferencesSearch.search(parameterObjectClass, GlobalSearchScope.projectScope(project)).findAll()
        cachedClassUsages = classReferences

        // Count unique functions that use this class as a parameter type
        val functionsUsingClass = mutableSetOf<PyFunction>()
        for (ref in classReferences) {
            val element = ref.element
            val param = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java)
            if (param != null) {
                val containingFunction = PsiTreeUtil.getParentOfType(param, PyFunction::class.java)
                if (containingFunction != null) {
                    functionsUsingClass.add(containingFunction)
                }
            }
        }

        return functionsUsingClass.size
    }

    fun hasUnsupportedTypeHintUsages(): Boolean {
        val project = function.project
        val context = TypeEvalContext.codeAnalysis(project, function.containingFile)

        val parameter = cachedParameter ?: findInlineableParameter(function, context)
        if (parameter == null) {
            LOG.debug("hasUnsupportedTypeHintUsages: No inlineable parameter found")
            return false
        }
        cachedParameter = parameter

        val parameterObjectClass = cachedParameterObjectClass ?: resolveParameterClass(parameter, context)
        if (parameterObjectClass == null) {
            LOG.debug("hasUnsupportedTypeHintUsages: Could not resolve parameter class")
            return false
        }
        cachedParameterObjectClass = parameterObjectClass

        val classReferences = cachedClassUsages
            ?: ReferencesSearch.search(parameterObjectClass, GlobalSearchScope.projectScope(project)).findAll()
        cachedClassUsages = classReferences

        return findUnsupportedTypeHintUsages(parameterObjectClass, classReferences).isNotEmpty()
    }

    fun run(
        settings: InlineParameterObjectSettings = InlineParameterObjectSettings(
            inlineAllOccurrences = true,
            removeClass = true
        )
    ) {
        val project = function.project
        LOG.debug("Starting Inline Parameter Object refactoring for function: ${function.name}")

        val result = runWithModalProgressBlocking(project, MyBundle.message("inline.parameter.object.command.name")) {
            readAction {
                val context = TypeEvalContext.codeAnalysis(project, function.containingFile)
                
                // Use cached parameter if available, otherwise find it
                val parameter = cachedParameter ?: findInlineableParameter(function, context)
                if (parameter == null) {
                    LOG.debug("run: No inlineable parameter found")
                    return@readAction null
                }

                // Use cached class if available
                val parameterObjectClass = cachedParameterObjectClass ?: resolveParameterClass(parameter, context)
                if (parameterObjectClass == null) {
                    LOG.debug("run: Could not resolve parameter class")
                    return@readAction null
                }

                val fields = extractFields(parameterObjectClass)
                if (fields.isEmpty()) {
                    LOG.debug("run: No fields found in parameter class")
                    return@readAction null
                }

                val isTypedDict = isTypedDict(parameterObjectClass, context)

                // Use cached references if available
                val classReferences = cachedClassUsages ?: ReferencesSearch.search(parameterObjectClass, GlobalSearchScope.projectScope(project)).findAll()

                // Identify functions to process
                val functionsToProcess = if (settings.inlineAllOccurrences) {
                    val functionsUsingClass = mutableSetOf<PyFunction>()
                    for (ref in classReferences) {
                        val element = ref.element
                        val param = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java)
                        if (param != null) {
                            val containingFunction = PsiTreeUtil.getParentOfType(param, PyFunction::class.java)
                            if (containingFunction != null) {
                                functionsUsingClass.add(containingFunction)
                            }
                        }
                    }
                    functionsUsingClass.toList()
                } else {
                    listOf(function)
                }

                // Create a plan for each function
                val plans = functionsToProcess.mapNotNull { targetFunction ->
                    val targetContext = if (targetFunction.containingFile == function.containingFile) {
                        context
                    } else {
                        TypeEvalContext.codeAnalysis(project, targetFunction.containingFile)
                    }

                    val targetParameter = findInlineableParameter(targetFunction, targetContext) ?: return@mapNotNull null
                    val parameterName = targetParameter.name ?: return@mapNotNull null

                    val functionUsages =
                        ReferencesSearch.search(targetFunction, GlobalSearchScope.projectScope(project)).findAll()

                    val (callSiteUpdates, consumedElements) = prepareCallSiteUpdates(
                        function = targetFunction,
                        parameter = targetParameter,
                        parameterObjectClass = parameterObjectClass,
                        fields = fields,
                        functionUsages = functionUsages,
                        inlineAllOccurrences = true, // Always inline all call sites for the target function
                        context = targetContext
                    )

                    val planConsumedElements = consumedElements.toMutableList()
                    val annotationValue = targetParameter.annotation?.value
                    if (annotationValue != null) {
                        if (annotationValue is PyReferenceExpression &&
                            annotationValue.reference.resolve() == parameterObjectClass
                        ) {
                            planConsumedElements.add(annotationValue)
                        }
                        val annotationRefs =
                            PsiTreeUtil.findChildrenOfType(annotationValue, PyReferenceExpression::class.java)
                        for (ref in annotationRefs) {
                            if (ref.reference.resolve() == parameterObjectClass) {
                                planConsumedElements.add(ref)
                            }
                        }
                    }

                    Plan(
                        project = project,
                        function = targetFunction,
                        parameter = targetParameter,
                        parameterName = parameterName,
                        parameterObjectClass = parameterObjectClass,
                        fields = fields,
                        functionUsages = functionUsages,
                        callSiteUpdates = callSiteUpdates,
                        isTypedDict = isTypedDict,
                        consumedElements = planConsumedElements
                    )
                }

                // Determine if class can be safely deleted
                val allClassUsages = classReferences.map { it.element }.toMutableSet()
                val allConsumedElements = plans.flatMap { it.consumedElements }.toSet()
                allClassUsages.removeAll(allConsumedElements)
                val canDeleteClass = allClassUsages.isEmpty()

                Triple(plans, parameterObjectClass, canDeleteClass)
            }
        } ?: return

        val (plans, parameterObjectClass, canDeleteClass) = result

        WriteCommandAction.runWriteCommandAction(
            project,
            MyBundle.message("inline.parameter.object.command.name"),
            null,
            {
            // Process all plans
            for (plan in plans) {
                updateFunctionBody(plan)
                replaceFunctionSignature(plan)
                applyCallSiteUpdates(plan)
                // Reformat each function individually to avoid PSI crashes
                if (plan.function.isValid) {
                    CodeStyleManager.getInstance(project).reformat(plan.function)
                }
            }

            // Remove the class if requested and no usages remain
            if (settings.removeClass && canDeleteClass) {
                if (parameterObjectClass.isValid) {
                    parameterObjectClass.delete()
                }
            }
        })
    }

    private fun replaceFunctionSignature(plan: Plan) {
        val generator = PyElementGenerator.getInstance(plan.project)
        val languageLevel = LanguageLevel.forElement(plan.function)

        val oldParams = plan.function.parameterList.parameters.toList()
        val newParamsText = buildString {
            append("(")
            var first = true
            for (p in oldParams) {
                val pieces: List<String> = if (p == plan.parameter) {
                    plan.fields.map { f ->
                        buildString {
                            append(f.name)
                            if (!f.typeText.isNullOrBlank()) {
                                append(": ")
                                append(f.typeText)
                            }
                            if (!f.defaultText.isNullOrBlank()) {
                                append(" = ")
                                append(f.defaultText)
                            }
                        }
                    }
                } else {
                    listOf(p.text)
                }

                for (piece in pieces) {
                    if (!first) append(", ")
                    append(piece)
                    first = false
                }
            }
            append(")")
        }

        val dummy = generator.createFromText(
            languageLevel,
            PyFunction::class.java,
            "def __inline_param_object$newParamsText:\\n    pass"
        )

        plan.function.parameterList.replace(dummy.parameterList)
    }

    private fun updateFunctionBody(plan: Plan) {
        val generator = PyElementGenerator.getInstance(plan.project)
        val fieldNames = plan.fields.map { it.name }.toSet()
        
        // Optimize: Collect both types in one pass
        val elements = PsiTreeUtil.findChildrenOfAnyType(
            plan.function,
            PyReferenceExpression::class.java,
            PySubscriptionExpression::class.java
        )

        for (element in elements) {
            if (!element.isValid) continue

            if (element is PyReferenceExpression) {
                // 1. Replace attribute access: params.field -> field
                val qualifier = element.qualifier as? PyReferenceExpression ?: continue
                if (qualifier.name != plan.parameterName) continue

                // Important: Verify the qualifier actually refers to the parameter being inlined
                if (qualifier.reference.resolve() != plan.parameter) continue

                val fieldName = element.name ?: continue
                if (!fieldNames.contains(fieldName)) continue

                ParameterObjectUtils.replaceExpression(generator, element, fieldName)
            } else if (plan.isTypedDict && element is PySubscriptionExpression) {
                // 2. Replace subscription access (TypedDict): params["field"] -> field
                val operand = element.operand as? PyReferenceExpression ?: continue
                if (operand.name != plan.parameterName) continue

                // Important: Verify the operand actually refers to the parameter being inlined
                if (operand.reference.resolve() != plan.parameter) continue

                val indexExpr = element.indexExpression as? PyStringLiteralExpression ?: continue
                val fieldName = indexExpr.stringValue
                if (!fieldNames.contains(fieldName)) continue

                ParameterObjectUtils.replaceExpression(generator, element, fieldName)
            }
        }
    }

    private fun prepareCallSiteUpdates(
        function: PyFunction,
        parameter: PyNamedParameter,
        parameterObjectClass: PyClass,
        fields: List<FieldInfo>,
        functionUsages: Collection<PsiReference>,
        inlineAllOccurrences: Boolean,
        context: TypeEvalContext
    ): Pair<List<CallSiteUpdateInfo>, List<PsiElement>> {
        if (functionUsages.isEmpty()) return Pair(emptyList(), emptyList())

        val resolveContext = PyResolveContext.defaultContext(context)

        val result = mutableListOf<CallSiteUpdateInfo>()
        val consumedElements = mutableListOf<PsiElement>()

        for (ref in functionUsages) {
            val element = ref.element
            val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: continue
            if (call.callee != element && call.callee?.reference?.resolve() != function) continue

            // If inlining only this occurrence, check if this is the invocation element
            if (!inlineAllOccurrences) {
                val isCurrentInvocation = PsiTreeUtil.isAncestor(call, invocationElement, false)
                if (!isCurrentInvocation) continue
            }

            val mapping = call.multiMapArguments(resolveContext).firstOrNull {
                it.callableType?.callable == function
            } ?: continue

            val argForParam = mapping.mappedParameters.entries.firstOrNull { it.value.parameter == parameter }?.key
                ?: continue

            val paramObjectExpr: PyExpression = when (argForParam) {
                is PyKeywordArgument -> argForParam.valueExpression
                else -> argForParam
            } ?: continue

            var ctorCall = paramObjectExpr as? PyCallExpression
            var isDirectCall = true
            if (ctorCall == null && paramObjectExpr is PyReferenceExpression) {
                val resolved = paramObjectExpr.reference.resolve()
                if (resolved is PyTargetExpression) {
                    val assignedValue = resolved.findAssignedValue()
                    if (assignedValue is PyCallExpression) {
                        ctorCall = assignedValue
                        isDirectCall = false
                    }
                }
            }

            if (ctorCall == null) continue

            val ctorCallee = ctorCall.callee as? PyReferenceExpression ?: continue
            val resolvedClass = ctorCallee.reference.resolve() as? PyClass ?: continue
            if (resolvedClass != parameterObjectClass) continue

            // If we are replacing a direct constructor call, we consume the reference to the class
            if (isDirectCall) {
                consumedElements.add(ctorCallee)
            }

            val ctorArgs = ctorCall.argumentList?.arguments ?: emptyArray()
            val hasKeywordArgs = ctorArgs.any { it is PyKeywordArgument }

            val fieldValues = LinkedHashMap<String, String>()
            val positional = ctorArgs.filterIsInstance<PyExpression>().filter { it !is PyKeywordArgument }
            for ((idx, field) in fields.withIndex()) {
                val kw = ctorArgs.filterIsInstance<PyKeywordArgument>().firstOrNull { it.keyword == field.name }
                if (kw != null) {
                    val v = kw.valueExpression?.text
                    if (v != null) fieldValues[field.name] = v
                    continue
                }
                val pos = positional.getOrNull(idx)?.text
                if (pos != null) fieldValues[field.name] = pos
            }

            // Build the new argument list by replacing only the argument that carried the parameter object.
            val callArgs = call.argumentList?.arguments ?: emptyArray()
            val argIndex = callArgs.indexOf(argForParam)
            val hasTrailingPositionalArgs = argIndex >= 0 &&
                    callArgs.drop(argIndex + 1).any { it !is PyKeywordArgument }

            val positionalFieldValues = buildList {
                for (field in fields) {
                    val value = fieldValues[field.name] ?: break
                    add(value)
                }
            }

            val newArgs = mutableListOf<String>()
            for (arg in callArgs) {
                if (arg == argForParam) {
                    if (arg is PyKeywordArgument) {
                        // params=MyParams(...) -> expand to field keywords
                        for ((k, v) in fieldValues) {
                            newArgs.add("$k=$v")
                        }
                    } else {
                        val usePositional =
                            hasTrailingPositionalArgs || !hasKeywordArgs
                        if (usePositional) {
                            // Preserve positional ordering when later positional args exist.
                            for (value in positionalFieldValues) {
                                newArgs.add(value)
                            }
                        } else {
                            for ((k, v) in fieldValues) {
                                newArgs.add("$k=$v")
                            }
                        }
                    }
                } else {
                    newArgs.add(arg.text)
                }
            }

            result.add(
                CallSiteUpdateInfo(
                    callExpression = call,
                    newArgumentListText = newArgs.joinToString(", ")
                )
            )
        }

        return Pair(result, consumedElements)
    }

    private fun applyCallSiteUpdates(plan: Plan) {
        if (plan.callSiteUpdates.isEmpty()) return

        val generator = PyElementGenerator.getInstance(plan.project)
        val languageLevel = LanguageLevel.forElement(function)

        for (updateInfo in plan.callSiteUpdates) {
            val call = updateInfo.callExpression
            val newArgsText = updateInfo.newArgumentListText

            ParameterObjectUtils.replaceArgumentList(generator, languageLevel, call, newArgsText, LOG)
        }
    }


    private fun findInlineableParameter(function: PyFunction, context: TypeEvalContext): PyNamedParameter? {
        return function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .firstOrNull { p ->
                val name = p.name ?: return@firstOrNull false
                if (p.isSelf || p.isPositionalContainer || p.isKeywordContainer) return@firstOrNull false
                if (name == "self" || name == "cls") return@firstOrNull false

                val cls = resolveParameterClass(p, context) ?: return@firstOrNull false

                val valid = isValidParameterObject(cls, context)
                val fields = extractFields(cls)

                valid && fields.isNotEmpty()
            }
    }

    private fun resolveParameterClass(parameter: PyNamedParameter, context: TypeEvalContext): PyClass? {
        val annotationValue = parameter.annotation?.value ?: return null
        return resolveParameterClassFromAnnotation(annotationValue, context)
    }

    private fun resolveParameterClassFromAnnotation(
        annotationValue: PyExpression,
        context: TypeEvalContext
    ): PyClass? {
        val candidates = collectAnnotationCandidates(annotationValue, context)
        val classes = candidates.mapNotNull { candidate ->
            (candidate as? PyReferenceExpression)?.reference?.resolve() as? PyClass
        }.distinct()
        return classes.singleOrNull()
    }

    private fun collectAnnotationCandidates(
        expression: PyExpression,
        context: TypeEvalContext
    ): List<PyExpression> {
        if (expression is PyNoneLiteralExpression) return emptyList()
        if (expression is PyReferenceExpression && expression.name == "None") return emptyList()
        if (expression.text == "None") return emptyList()

        return when (expression) {
            is PyParenthesizedExpression -> {
                val inner = expression.containedExpression ?: return emptyList()
                collectAnnotationCandidates(inner, context)
            }

            is PyBinaryExpression -> {
                if (expression.operator == PyTokenTypes.OR) {
                    val left = collectAnnotationCandidates(expression.leftExpression, context)
                    val right =
                        expression.rightExpression?.let { collectAnnotationCandidates(it, context) } ?: emptyList()
                    left + right
                } else {
                    listOf(expression)
                }
            }

            is PySubscriptionExpression -> {
                val operand = expression.operand
                val index = expression.indexExpression ?: return listOf(expression)
                val qualifiedNames = PyTypingTypeProvider.resolveToQualifiedNames(operand, context)
                val operandName = (operand as? PyReferenceExpression)?.name
                val operandText = operand.text

                fun matchesAny(vararg names: String): Boolean {
                    return qualifiedNames.any { it in names } ||
                            operandName in names ||
                            operandText in names
                }

                if (matchesAny("typing.Optional", "Optional", "typing_extensions.Optional")) {
                    return collectAnnotationCandidates(index, context)
                }

                if (matchesAny("typing.Union", "Union", "typing_extensions.Union")) {
                    val elements = if (index is PyTupleExpression) index.elements.toList() else listOf(index)
                    return elements.flatMap { collectAnnotationCandidates(it, context) }
                }

                if (matchesAny("typing.Annotated", "Annotated", "typing_extensions.Annotated")) {
                    val elements = if (index is PyTupleExpression) index.elements.toList() else listOf(index)
                    val first = elements.firstOrNull() ?: return emptyList()
                    return collectAnnotationCandidates(first, context)
                }

                listOf(expression)
            }

            else -> listOf(expression)
        }
    }

    private fun isValidParameterObject(pyClass: PyClass, context: TypeEvalContext): Boolean {
        if (isDataclass(pyClass)) return true
        if (isTypedDict(pyClass, context)) return true

        val superClasses = pyClass.getSuperClasses(context)
        for (superClass in superClasses) {
            val qName = superClass.qualifiedName
            if (qName == "typing.NamedTuple" || superClass.name == "NamedTuple") return true
            if (qName == "pydantic.BaseModel" || qName == "pydantic.main.BaseModel" || superClass.name == "BaseModel") return true
        }
        return false
    }

    private fun isDataclass(pyClass: PyClass): Boolean {
        val decorators = pyClass.decoratorList?.decorators ?: return false
        return decorators.any { d ->
            val callee = d.callee as? PyReferenceExpression
            callee?.name == "dataclass"
        }
    }

    private fun isTypedDict(pyClass: PyClass, context: TypeEvalContext): Boolean {
        val superClasses = pyClass.getSuperClasses(context)
        if (superClasses.any {
            it.qualifiedName == "typing.TypedDict" ||
                    it.qualifiedName == "typing_extensions.TypedDict" ||
                    it.name == "TypedDict"
        }) return true

        return pyClass.superClassExpressions.any { expr ->
            val text = expr.text
            text == "TypedDict" || text == "typing.TypedDict" || text == "typing_extensions.TypedDict"
        }
    }

    private fun extractFields(pyClass: PyClass): List<FieldInfo> {
        val result = mutableListOf<FieldInfo>()
        val statements = pyClass.statementList.statements
        for (st in statements) {
            val target: PyTargetExpression?
            val defaultText: String?

            val assignment = st as? PyAssignmentStatement
            if (assignment != null) {
                target = assignment.targets.singleOrNull() as? PyTargetExpression
                defaultText = assignment.assignedValue?.text
            } else {
                target = PsiTreeUtil.findChildOfType(st, PyTargetExpression::class.java, false)
                defaultText = null
            }

            val name = target?.name ?: continue

            if (name == "model_config") continue

            val typeText = target.annotation?.value?.text

            if (typeText == null && defaultText == null) continue

            result.add(FieldInfo(name = name, typeText = typeText, defaultText = defaultText))
        }
        return result
    }

    private fun findUnsupportedTypeHintUsages(
        parameterObjectClass: PyClass,
        classReferences: Collection<PsiReference>
    ): List<PsiElement> {
        val blockedUsages = LinkedHashSet<PsiElement>()
        val candidateFiles = LinkedHashSet<PsiFile>()
        val project = parameterObjectClass.project

        candidateFiles.add(parameterObjectClass.containingFile)
        for (ref in classReferences) {
            candidateFiles.add(ref.element.containingFile)
        }

        val className = parameterObjectClass.name
        if (!className.isNullOrBlank()) {
            val scope = GlobalSearchScope.projectScope(project)
            val searchHelper = PsiSearchHelper.getInstance(project)
            searchHelper.processElementsWithWord(
                { element, _ ->
                    candidateFiles.add(element.containingFile)
                    true
                },
                scope,
                className,
                UsageSearchContext.IN_CODE,
                true
            )
        }

        fun hasBlockedAnnotation(owner: PyAnnotationOwner?): Boolean {
            return when (owner) {
                is PyFunction -> true
                is PyNamedParameter -> false
                else -> owner != null
            }
        }

        for (file in candidateFiles) {
            val annotations = PsiTreeUtil.findChildrenOfType(file, PyAnnotation::class.java)
            for (annotation in annotations) {
                val owner = PsiTreeUtil.getParentOfType(annotation, PyAnnotationOwner::class.java, true)
                if (!hasBlockedAnnotation(owner)) continue

                val refs = PsiTreeUtil.findChildrenOfType(annotation, PyReferenceExpression::class.java)
                for (ref in refs) {
                    if (ref.reference.resolve() == parameterObjectClass) {
                        blockedUsages.add(ref)
                    }
                }
            }

            val typeDecls = PsiTreeUtil.findChildrenOfType(file, PyTypeDeclarationStatement::class.java)
            for (decl in typeDecls) {
                val refs = PsiTreeUtil.findChildrenOfType(decl, PyReferenceExpression::class.java)
                for (ref in refs) {
                    if (ref.reference.resolve() == parameterObjectClass) {
                        blockedUsages.add(ref)
                    }
                }
            }
        }

        return blockedUsages.toList()
    }

    companion object {
        private val LOG = logger<PyInlineParameterObjectProcessor>()

        fun hasInlineableParameterObject(function: PyFunction): Boolean {
            return try {
                val context = TypeEvalContext.codeAnalysis(function.project, function.containingFile)
                PyInlineParameterObjectProcessor(function, function).findInlineableParameter(function, context) != null
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Throwable) {
                LOG.warn("Error checking for inlineable parameter object", e)
                false
            }
        }
    }
}
