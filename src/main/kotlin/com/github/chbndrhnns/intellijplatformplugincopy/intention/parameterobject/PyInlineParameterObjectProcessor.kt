package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil

class PyInlineParameterObjectProcessor(
    private val function: PyFunction,
    @Suppress("unused") private val invocationElement: PsiElement,
) {

    data class FieldInfo(
        val name: String,
        val typeText: String?,
        val defaultText: String?,
    )

    private data class Plan(
        val project: Project,
        val parameter: PyNamedParameter,
        val parameterName: String,
        val parameterObjectClass: PyClass,
        val fields: List<FieldInfo>,
        val functionUsages: Collection<PsiReference>,
        val callSiteUpdates: List<CallSiteUpdateInfo>,
    )

    private data class CallSiteUpdateInfo(
        val callExpression: PyCallExpression,
        val newArgumentListText: String,
    )

    fun run() {
        val project = function.project

        val plan = runWithModalProgressBlocking(project, "Inline parameter object") {
            readAction {
                val parameter = findInlineableParameter(function)
                    ?: return@readAction null

                val parameterName = parameter.name ?: return@readAction null
                val parameterObjectClass = resolveParameterClass(parameter) ?: return@readAction null
                val fields = extractDataclassFields(parameterObjectClass)
                if (fields.isEmpty()) return@readAction null

                val functionUsages = ReferencesSearch.search(function, GlobalSearchScope.projectScope(project)).findAll()
                val callSiteUpdates = prepareCallSiteUpdates(
                    project = project,
                    function = function,
                    parameter = parameter,
                    parameterName = parameterName,
                    parameterObjectClass = parameterObjectClass,
                    fields = fields,
                    functionUsages = functionUsages
                )

                Plan(
                    project = project,
                    parameter = parameter,
                    parameterName = parameterName,
                    parameterObjectClass = parameterObjectClass,
                    fields = fields,
                    functionUsages = functionUsages,
                    callSiteUpdates = callSiteUpdates,
                )
            }
        } ?: return

        WriteCommandAction.runWriteCommandAction(project, "Inline parameter object", null, Runnable {
            replaceFunctionSignature(plan)
            updateFunctionBody(plan)
            applyCallSiteUpdates(plan)
            CodeStyleManager.getInstance(project).reformat(function.containingFile)
        }, function.containingFile)
    }

    private fun replaceFunctionSignature(plan: Plan) {
        val generator = PyElementGenerator.getInstance(plan.project)
        val languageLevel = LanguageLevel.forElement(function)

        val oldParams = function.parameterList.parameters.toList()
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
            "def __inline_param_object$newParamsText:\n    pass"
        )

        function.parameterList.replace(dummy.parameterList)
    }

    private fun updateFunctionBody(plan: Plan) {
        val generator = PyElementGenerator.getInstance(plan.project)
        val fieldNames = plan.fields.map { it.name }.toSet()

        val references = PsiTreeUtil.collectElementsOfType(function, PyReferenceExpression::class.java)
        for (ref in references) {
            if (!ref.isValid) continue

            val qualifier = ref.qualifier as? PyReferenceExpression ?: continue
            if (qualifier.name != plan.parameterName) continue

            val fieldName = ref.name ?: continue
            if (!fieldNames.contains(fieldName)) continue

            val languageLevel = LanguageLevel.forElement(ref)
            val newExpr = generator.createExpressionFromText(languageLevel, fieldName)
            if (PyReplaceExpressionUtil.isNeedParenthesis(ref, newExpr)) {
                val parenthesized = generator.createExpressionFromText(languageLevel, "($fieldName)")
                ref.replace(parenthesized)
            } else {
                ref.replace(newExpr)
            }
        }
    }

    private fun prepareCallSiteUpdates(
        project: Project,
        function: PyFunction,
        parameter: PyNamedParameter,
        parameterName: String,
        parameterObjectClass: PyClass,
        fields: List<FieldInfo>,
        functionUsages: Collection<PsiReference>,
    ): List<CallSiteUpdateInfo> {
        if (functionUsages.isEmpty()) return emptyList()

        val resolveContext = PyResolveContext.defaultContext(
            TypeEvalContext.codeAnalysis(project, function.containingFile)
        )

        val result = mutableListOf<CallSiteUpdateInfo>()
        for (ref in functionUsages) {
            val element = ref.element
            val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: continue
            if (call.callee != element && call.callee?.reference?.resolve() != function) continue

            val mapping = call.multiMapArguments(resolveContext).firstOrNull {
                it.callableType?.callable == function
            } ?: continue

            val argForParam = mapping.mappedParameters.entries.firstOrNull { it.value.parameter == parameter }?.key
                ?: continue

            val paramObjectExpr: PyExpression = when (argForParam) {
                is PyKeywordArgument -> argForParam.valueExpression
                else -> argForParam
            } ?: continue

            val ctorCall = paramObjectExpr as? PyCallExpression ?: continue
            val ctorCallee = ctorCall.callee as? PyReferenceExpression ?: continue
            val resolvedClass = ctorCallee.reference.resolve() as? PyClass ?: continue
            if (resolvedClass != parameterObjectClass) continue

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
            val newArgs = mutableListOf<String>()
            for (arg in call.argumentList?.arguments ?: emptyArray()) {
                if (arg == argForParam) {
                    if (arg is PyKeywordArgument) {
                        // params=MyParams(...) -> expand to field keywords
                        for ((k, v) in fieldValues) {
                            newArgs.add("$k=$v")
                        }
                    } else {
                        if (hasKeywordArgs) {
                            for ((k, v) in fieldValues) {
                                newArgs.add("$k=$v")
                            }
                        } else {
                            // Positional ctor call -> positional function call
                            for (field in fields) {
                                val v = fieldValues[field.name] ?: continue
                                newArgs.add(v)
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

        return result
    }

    private fun applyCallSiteUpdates(plan: Plan) {
        if (plan.callSiteUpdates.isEmpty()) return

        val generator = PyElementGenerator.getInstance(plan.project)
        val languageLevel = LanguageLevel.forElement(function)

        for (updateInfo in plan.callSiteUpdates) {
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
        }
    }

    private fun findInlineableParameter(function: PyFunction): PyNamedParameter? {
        return function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .firstOrNull { p ->
                val name = p.name ?: return@firstOrNull false
                if (p.isSelf || p.isPositionalContainer || p.isKeywordContainer) return@firstOrNull false
                if (name == "self" || name == "cls") return@firstOrNull false

                val cls = resolveParameterClass(p) ?: return@firstOrNull false
                isDataclass(cls) && extractDataclassFields(cls).isNotEmpty()
            }
    }

    private fun resolveParameterClass(parameter: PyNamedParameter): PyClass? {
        val annotationValue = parameter.annotation?.value ?: return null
        val ref = annotationValue as? PyReferenceExpression ?: return null
        return ref.reference.resolve() as? PyClass
    }

    private fun isDataclass(pyClass: PyClass): Boolean {
        val decorators = pyClass.decoratorList?.decorators ?: return false
        return decorators.any { d ->
            val callee = d.callee as? PyReferenceExpression
            callee?.name == "dataclass"
        }
    }

    private fun extractDataclassFields(pyClass: PyClass): List<FieldInfo> {
        val result = mutableListOf<FieldInfo>()
        val statements = pyClass.statementList.statements
        for (st in statements) {
            // Handles both:
            // - annotated assignment without value: `x: int`
            // - annotated assignment with value: `x: int = 1`
            // - plain assignment with value: `x = 1` (type may be null)

            val target: PyTargetExpression?
            val defaultText: String?

            val assignment = st as? PyAssignmentStatement
            if (assignment != null) {
                target = assignment.targets.singleOrNull() as? PyTargetExpression
                defaultText = assignment.assignedValue?.text
            } else {
                // For annotation-only statements, the PSI may not be a PyAssignmentStatement.
                target = PsiTreeUtil.findChildOfType(st, PyTargetExpression::class.java, false)
                defaultText = null
            }

            val name = target?.name ?: continue
            val typeText = target.annotation?.value?.text

            // Only treat it as a field if it has at least an annotation or a default value.
            if (typeText == null && defaultText == null) continue

            result.add(FieldInfo(name = name, typeText = typeText, defaultText = defaultText))
        }
        return result
    }

    companion object {
        private val LOG = logger<PyInlineParameterObjectProcessor>()

        fun hasInlineableParameterObject(function: PyFunction): Boolean {
            return try {
                PyInlineParameterObjectProcessor(function, function).findInlineableParameter(function) != null
            } catch (_: Throwable) {
                false
            }
        }
    }
}
