package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil

/**
 * Encapsulates PSI rewrites for the "Introduce custom type from stdlib" feature.
 *
 * Behaviour is a direct extraction from IntroduceCustomTypeFromStdlibIntention so
 * that all existing tests keep passing:
 * - Rewriting annotations to use the new custom type.
 * - Wrapping expressions at call sites.
 * - Updating parameter annotations when invoked from a call-site.
 * - Synchronising dataclass field annotations and wrapping constructor usages
 *   within a single file.
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
        expr.replace(wrapped)
    }

    /**
     * When the intention is started from a call-site expression, update the
     * corresponding parameter annotation in the resolved callable so that it
     * refers to [newTypeName] instead of the builtin.
     */
    fun updateParameterAnnotationFromCallSite(
        expr: PyExpression,
        builtinName: String,
        newTypeName: String,
        generator: PyElementGenerator,
    ) {
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java, false) ?: return
        val call = argList.parent as? PyCallExpression ?: return

        val context = TypeEvalContext.codeAnalysis(expr.project, expr.containingFile)
        val resolveContext = PyResolveContext.defaultContext(context)
        val mappingList = call.multiMapArguments(resolveContext)
        val mapping = mappingList.firstOrNull() ?: return
        val mappedParamWrapper = mapping.mappedParameters[expr] ?: return
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
                copy.replace(newTypeRef)
        }

        return copy.text
    }

    private fun updateParameterType(parameter: PyNamedParameter, newTypeAnnotation: String) {
        val project = parameter.project
        val generator = PyElementGenerator.getInstance(project)
        val defaultValue = parameter.defaultValueText
        val newParameter = generator.createParameter(
            parameter.name!!,
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
     * Synchronise the dataclass field's annotation with the newly introduced
     * type. This mirrors the inline behaviour previously used when
     * target.annotationRef == null.
     */
    fun syncDataclassFieldAnnotation(
        field: PyTargetExpression,
        builtinName: String,
        newTypeRef: PyExpression,
    ) {
        val typeDecl = PsiTreeUtil.getParentOfType(field, PyTypeDeclarationStatement::class.java, false)
        val annExpr = typeDecl?.annotation?.value
        if (annExpr != null) {
            // Replace the builtin reference inside the annotation with the new
            // type reference, falling back to a plain replacement when the
            // annotation is just the builtin name.
            val builtinRefInAnn = PsiTreeUtil.findChildOfType(annExpr, PyReferenceExpression::class.java)
            val replacement = newTypeRef.copy() as PyExpression
            when {
                builtinRefInAnn != null && builtinRefInAnn.name == builtinName ->
                    builtinRefInAnn.replace(replacement)

                annExpr.text == builtinName ->
                    annExpr.replace(replacement)
            }
        }
    }

    /**
     * Wrap all constructor usages of the given dataclass [field] with the
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

        val calls = PsiTreeUtil.collectElementsOfType(file, PyCallExpression::class.java)
        for (call in calls) {
            val callee = call.callee as? PyReferenceExpression ?: continue
            val resolved = callee.reference.resolve()
            if (resolved != pyClass) continue

            val argList = call.argumentList ?: continue

            // 1) Prefer keyword arguments matching the field name.
            val keywordArg = argList.arguments
                .filterIsInstance<PyKeywordArgument>()
                .firstOrNull { it.keyword == fieldName }
            if (keywordArg != null) {
                val valueExpr = keywordArg.valueExpression ?: continue
                wrapArgumentExpressionIfNeeded(valueExpr, wrapperTypeName, generator)
                continue
            }

            // 2) Fallback: positional arguments mapped by field index.
            val fields = pyClass.classAttributes
            val fieldIndex = fields.indexOfFirst { it.name == fieldName }
            if (fieldIndex < 0) continue

            val allArgs = argList.arguments.toList()
            val positionalArgs = allArgs.filter { it !is PyKeywordArgument }
            if (fieldIndex >= positionalArgs.size) continue

            val valueExpr = positionalArgs[fieldIndex] ?: continue
            wrapArgumentExpressionIfNeeded(valueExpr, wrapperTypeName, generator)
        }
    }

    private fun wrapArgumentExpressionIfNeeded(
        expr: PyExpression,
        wrapperTypeName: String,
        generator: PyElementGenerator,
    ) {
        // Avoid double-wrapping when the argument is already wrapped with the
        // custom type, e.g. Productid(Productid(123)).
        val existingCall = expr as? PyCallExpression
        if (existingCall != null) {
            val calleeText = existingCall.callee?.text
            if (calleeText == wrapperTypeName) return
        }

        val wrapped = generator.createExpressionFromText(
            LanguageLevel.getLatest(),
            "$wrapperTypeName(${expr.text})",
        )
        expr.replace(wrapped)
    }
}
