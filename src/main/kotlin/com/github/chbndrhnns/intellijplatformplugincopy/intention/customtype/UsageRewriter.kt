// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil

/**
 * Encapsulates PSI rewrites for the "Introduce custom type from stdlib" feature.
 */
class UsageRewriter {

    /**
     * Replace the referenced builtin in an annotation with [newTypeRef].
     *
     * For subscripted container annotations (e.g. ``dict[str, list[int]]``),
     * we keep the existing index expression and only swap out the operand.
     *
     * Supports forward references (string literals) by replacing the type name
     * inside the string.
     */
    fun rewriteAnnotation(annotationElement: PyExpression, newTypeRef: PyExpression, oldTypeName: String) {
        if (annotationElement is PyStringLiteralExpression) {
            val oldContent = annotationElement.stringValue
            val newContent = oldContent.replace(Regex("\\b$oldTypeName\\b"), newTypeRef.text)
            val generator = PyElementGenerator.getInstance(annotationElement.project)
            val newExpr = generator.createStringLiteral(annotationElement, newContent)
            PyReplaceExpressionUtil.replaceExpression(annotationElement, newExpr)
            return
        }

        val parentSub = annotationElement.parent as? PySubscriptionExpression
        if (parentSub != null && parentSub.operand == annotationElement) {
            // Replace the whole subscription expression (e.g. ``list[int]``)
            // with the new custom type (e.g. ``CustomList``)
            PyReplaceExpressionUtil.replaceExpression(parentSub, newTypeRef)
        } else {
            PyReplaceExpressionUtil.replaceExpression(annotationElement, newTypeRef)
        }
    }

    /**
     * Wrap [expr] with a call to [newTypeName], preserving the original
     * expression text as the sole argument.
     */
    fun wrapExpression(expr: PyExpression, newTypeName: String, generator: PyElementGenerator) {
        val exprText = expr.text
        val wrapped = generator.createExpressionFromText(
            LanguageLevel.getLatest(),
            "$newTypeName($exprText)",
        )
        PyReplaceExpressionUtil.replaceExpression(expr, wrapped)
    }

    /**
     * When the intention is started from a call-site expression, update the
     * corresponding parameter annotation in the resolved callable.
     */
    fun updateParameterAnnotationFromCallSite(
        expr: PyExpression,
        builtinName: String,
        newTypeName: String,
        generator: PyElementGenerator,
    ) {
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java, false) ?: return
        val call = argList.parent as? PyCallExpression ?: return

        // Use codeInsightFallback to ensure we can resolve synthetic members if needed
        val context = TypeEvalContext.codeInsightFallback(expr.project)
        val resolveContext = PyResolveContext.defaultContext(context)

        val mappingList = call.multiMapArguments(resolveContext)
        val mapping = mappingList.firstOrNull() ?: return
        val mappedParamWrapper = mapping.mappedParameters[expr] ?: return

        // We need the physical parameter to update its annotation.
        val mappedParam = mappedParamWrapper.parameter as? PyNamedParameter ?: return

        val parameter =
            if (mappedParam.containingFile != expr.containingFile && expr.containingFile.originalFile == mappedParam.containingFile) {
                PsiTreeUtil.findSameElementInCopy(mappedParam, expr.containingFile)
            } else if (!expr.isPhysical && mappedParam.containingFile != expr.containingFile) {
                return
            } else {
                mappedParam
            }

        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return
        val paramIndex = function.parameterList.parameters.indexOf(parameter)

        val newAnnotationText = computeNewAnnotationText(parameter, builtinName, newTypeName, generator)

        updateParameterType(parameter, newAnnotationText)

        updateOverrides(function, paramIndex, newAnnotationText)
    }

    private fun computeNewAnnotationText(
        parameter: PyNamedParameter,
        builtinName: String,
        newTypeName: String,
        generator: PyElementGenerator
    ): String {
        val annotation = parameter.annotation
        val annExpr = annotation?.value ?: return newTypeName

        val copy = annExpr.copy() as PyExpression
        val newTypeRef = generator.createExpressionFromText(LanguageLevel.getLatest(), newTypeName)

        val builtinRefInAnn = PsiTreeUtil.collectElementsOfType(copy, PyReferenceExpression::class.java)
            .firstOrNull { it.name == builtinName }

        when {
            builtinRefInAnn != null -> {
                if (builtinRefInAnn == copy) {
                    return newTypeName
                }
                rewriteAnnotation(builtinRefInAnn, newTypeRef, builtinName)
            }

            copy is PyStringLiteralExpression ->
                rewriteAnnotation(copy, newTypeRef, builtinName)

            else ->
                PyReplaceExpressionUtil.replaceExpression(copy, newTypeRef)
        }

        return copy.text
    }

    private fun updateParameterType(parameter: PyNamedParameter, newTypeAnnotation: String) {
        val paramName = parameter.name ?: return
        val project = parameter.project
        val generator = PyElementGenerator.getInstance(project)
        val defaultValue = parameter.defaultValueText
        val newParameter = generator.createParameter(
            paramName,
            defaultValue,
            newTypeAnnotation,
            LanguageLevel.forElement(parameter)
        )
        parameter.replace(newParameter)
    }

    private fun updateOverrides(baseFunction: PyFunction, paramIndex: Int, newTypeAnnotation: String) {
        val overrides = PyOverridingMethodsSearch.search(baseFunction, true).findAll()
        for (override in overrides) {
            val parameters = override.parameterList.parameters
            if (paramIndex < parameters.size) {
                val param = parameters[paramIndex]
                if (param is PyNamedParameter) {
                    updateParameterType(param, newTypeAnnotation)
                }
            }
        }
    }

    /**
     * Wraps arguments at call sites when a function parameter type is updated.
     */
    fun wrapFunctionUsages(
        function: PyFunction,
        parameter: PyNamedParameter,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        val paramName = parameter.name ?: return
        // Limit search to the containing file for now (consistent with existing logic guarantees)
        // For project-wide support, usage scope could be expanded.
        val scope = GlobalSearchScope.fileScope(function.containingFile)
        val references = ReferencesSearch.search(function, scope).findAll()

        val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(function.project))

        for (ref in references) {
            val element = ref.element
            val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: continue

            // Ensure we are looking at the function call
            if (call.callee?.reference?.resolve() != function) continue

            val mappingList = call.multiMapArguments(resolveContext)
            for (mapping in mappingList) {
                for ((arg, paramWrapper) in mapping.mappedParameters) {
                    if (paramWrapper.name == paramName) {
                        val realArg = when (arg) {
                            is PyKeywordArgument -> arg.valueExpression
                            is PyStarArgument -> null
                            else -> arg
                        }
                        if (realArg != null) {
                            wrapArgumentExpressionIfNeeded(realArg, wrapperTypeName, generator)
                        }
                    }
                }
            }
        }
    }

    /**
     * PHASE 1: Search for usages to wrap. Safe to run in Read Action / Background Thread.
     */
    fun findUsagesToWrap(
        function: PyFunction,
        parameter: PyNamedParameter
    ): List<SmartPsiElementPointer<PyExpression>> {
        val paramName = parameter.name ?: return emptyList()
        val scope = GlobalSearchScope.fileScope(function.containingFile)
        val references = ReferencesSearch.search(function, scope).findAll()

        val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(function.project))
        val pointerManager = SmartPointerManager.getInstance(function.project)
        val results = mutableListOf<SmartPsiElementPointer<PyExpression>>()

        for (ref in references) {
            val element = ref.element
            val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: continue

            if (call.callee?.reference?.resolve() != function) continue

            val mappingList = call.multiMapArguments(resolveContext)
            for (mapping in mappingList) {
                for ((arg, paramWrapper) in mapping.mappedParameters) {
                    if (paramWrapper.name == paramName) {
                        val realArg = when (arg) {
                            is PyKeywordArgument -> arg.valueExpression
                            is PyStarArgument -> null
                            else -> arg
                        }
                        if (realArg != null) {
                            results.add(pointerManager.createSmartPsiElementPointer(realArg))
                        }
                    }
                }
            }
        }
        return results
    }

    /**
     * PHASE 2: Modify the found usages. Must run in Write Action.
     */
    fun wrapUsages(
        usages: List<SmartPsiElementPointer<PyExpression>>,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        for (ptr in usages) {
            val element = ptr.element ?: continue
            wrapArgumentExpressionIfNeeded(element, wrapperTypeName, generator)
        }
    }

    /**
     * Synchronise the dataclass field's annotation with the newly introduced type.
     */
    fun syncDataclassFieldAnnotation(
        field: PyTargetExpression,
        builtinName: String,
        newTypeRef: PyExpression,
    ) {
        val typeDecl = PsiTreeUtil.getParentOfType(field, PyTypeDeclarationStatement::class.java, false)
        val annExpr = typeDecl?.annotation?.value
        if (annExpr != null) {
            val builtinRefInAnn = PsiTreeUtil.findChildOfType(annExpr, PyReferenceExpression::class.java)
            val replacement = newTypeRef.copy() as PyExpression
            when {
                builtinRefInAnn != null && builtinRefInAnn.name == builtinName ->
                    PyReplaceExpressionUtil.replaceExpression(builtinRefInAnn, replacement)

                annExpr.text == builtinName ->
                    PyReplaceExpressionUtil.replaceExpression(annExpr, replacement)
            }
        }
    }

    /**
     * Wrap all constructor usages of the given dataclass/Pydantic [field] with the
     * provided wrapper type within a single [file].
     */
    fun wrapDataclassConstructorUsages(
        file: PyFile,
        field: PyTargetExpression,
        wrapperTypeName: String,
        generator: PyElementGenerator,
    ) {
        val pyClass = PsiTreeUtil.getParentOfType(field, PyClass::class.java) ?: return
        val fieldName = field.name ?: return

        // Use codeInsightFallback to ensure synthetic members (like Pydantic's __init__) are resolved.
        val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(file.project))

        val calls = PsiTreeUtil.collectElementsOfType(file, PyCallExpression::class.java)
        for (call in calls) {
            val callee = call.callee as? PyReferenceExpression ?: continue
            if (callee.reference.resolve() != pyClass) continue

            val mappingList = call.multiMapArguments(resolveContext)

            var handled = false
            for (mapping in mappingList) {
                for ((arg, paramWrapper) in mapping.mappedParameters) {
                    // Use paramWrapper.name to match the field name.
                    // This handles both physical parameters (dataclasses) and synthetic ones (Pydantic),
                    // where paramWrapper.parameter might be null.
                    if (paramWrapper.name == fieldName) {
                        // Extract the value from the keyword argument to avoid wrapping the whole `k=v` expression.
                        val realArg = when (arg) {
                            is PyKeywordArgument -> arg.valueExpression
                            is PyStarArgument -> null // Skip *args and **kwargs
                            else -> arg
                        }

                        if (realArg != null) {
                            wrapArgumentExpressionIfNeeded(realArg, wrapperTypeName, generator)
                            handled = true
                        }
                    }
                }
            }

            if (!handled) {
                val argList = call.argumentList ?: continue
                for (arg in argList.arguments) {
                    if (arg is PyKeywordArgument && arg.keyword == fieldName) {
                        val valueExpr = arg.valueExpression
                        if (valueExpr != null) {
                            wrapArgumentExpressionIfNeeded(valueExpr, wrapperTypeName, generator)
                        }
                    }
                }
            }
        }
    }

    private fun wrapArgumentExpressionIfNeeded(
        expr: PyExpression,
        wrapperTypeName: String,
        generator: PyElementGenerator,
    ) {
        val existingCall = expr as? PyCallExpression
        if (existingCall != null) {
            val calleeText = existingCall.callee?.text
            if (calleeText == wrapperTypeName) return
        }

        val wrapped = generator.createExpressionFromText(
            LanguageLevel.getLatest(),
            "$wrapperTypeName(${expr.text})",
        )
        PyReplaceExpressionUtil.replaceExpression(expr, wrapped)
    }
}