package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

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
     * we keep the existing index expression and only swap out the operand so
     * that ``dict`` becomes ``CustomDict`` but the concrete type arguments are
     * preserved, yielding ``CustomDict[str, list[int]]``.
     */
    fun rewriteAnnotation(annotationRef: PyReferenceExpression, newTypeRef: PyExpression) {
        val parentSub = annotationRef.parent as? PySubscriptionExpression
        if (parentSub != null && parentSub.operand == annotationRef) {
            // ``annotationRef`` is the callee part of a subscription; replace
            // just that part and leave the index-expression intact.
            parentSub.operand.replace(newTypeRef)
        } else {
            annotationRef.replace(newTypeRef)
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
        newTypeName: String,
        generator: PyElementGenerator,
    ) {
        // Locate the argument list and enclosing call for the expression.
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java, false) ?: return
        val call = argList.parent as? PyCallExpression ?: return
        val callee = call.callee as? PyReferenceExpression ?: return
        val resolved = callee.reference.resolve() as? PyFunction ?: return

        // Map the expression back to the corresponding parameter, handling
        // the straightforward positional and keyword cases covered by tests.
        val parameter: PyNamedParameter =
            when (val kwArg = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)) {
                null -> {
                    val args = argList.arguments.toList()
                    val positionalArgs = args.filter { it !is PyKeywordArgument }
                    val posIndex = positionalArgs.indexOf(expr)
                    if (posIndex < 0) return

                    val params = resolved.parameterList.parameters
                    if (posIndex >= params.size) return
                    params[posIndex] as? PyNamedParameter ?: return
                }

                else -> {
                    if (kwArg.valueExpression != expr) return
                    val name = kwArg.keyword ?: return
                    resolved.parameterList.findParameterByName(name) ?: return
                }
            }

        val annotation = parameter.annotation ?: return
        val annExpr = annotation.value ?: return

        val replacement = generator.createExpressionFromText(LanguageLevel.getLatest(), newTypeName)

        // For call‑site initiated intentions we always fully replace the
        // annotation's value expression with the new custom type. This keeps
        // the produced text simple and predictable (e.g. ``s: Customint``),
        // which is exactly what tests assert.
        annExpr.replace(replacement)
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
