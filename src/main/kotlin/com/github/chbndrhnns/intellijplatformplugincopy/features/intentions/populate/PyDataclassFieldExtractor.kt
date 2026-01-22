package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.populate

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Extracts field information from Python `@dataclass` definitions (and compatible shapes) using PSI.
 */
class PyDataclassFieldExtractor {

    fun extractDataclassFields(pyClass: PyClass, context: TypeEvalContext): List<FieldSpec> {
        val resolveCtx = PyResolveContext.defaultContext(context)

        // If class has an explicit __init__/__new__, prefer that signature.
        pyClass.findInitOrNew(false, context)?.let { init ->
            val callable = context.getType(init) as? PyCallableType
            val params = callable?.getParameters(context).orEmpty()
            return params
                .asSequence()
                .filter { it.name != null && !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }
                .mapNotNull { param ->
                    val name = param.name ?: return@mapNotNull null
                    // Guard against providers that may return a callable signature containing parameters
                    // that don't actually correspond to fields of this dataclass.
                    // (This can lead to nested dataclass values receiving sibling parameters.)
                    val classAttr = pyClass.findClassAttribute(name, /* inherited = */ true, context)
                        ?: return@mapNotNull null
                    val alias = classAttr?.let { extractAliasFromTarget(it, resolveCtx) }
                    FieldSpec(
                        name = alias?.first ?: name,
                        type = param.getType(context),
                        aliasName = alias?.second?.first,
                        aliasElement = alias?.second?.second
                    )
                }
                .toList()
        }

        // Synthetic __init__: fall back to inspecting class attributes with annotations or field(...) calls.
        return pyClass.classAttributes
            .asSequence()
            .filter { attr -> attr.annotation != null || attr.findAssignedValue() is PyCallExpression }
            .mapNotNull { attr ->
                val name = attr.name ?: return@mapNotNull null
                val alias = extractAliasFromTarget(attr, resolveCtx)
                FieldSpec(
                    name = alias?.first ?: name,
                    type = context.getType(attr),
                    aliasName = alias?.second?.first,
                    aliasElement = alias?.second?.second
                )
            }
            .toList()
    }

    /**
     * Returns a pair of (effectiveFieldName, (aliasName, aliasElement)) when an alias is detected via PSI.
     * Supported patterns:
     *  - dataclasses.field(alias=..., name=...)  [Pydantic uses alias]
     *  - pydantic.Field(alias=...)
     *  - Annotated[T, Alias(...)] or Annotated[T, "name"] (best‑effort)
     */
    private fun extractAliasFromTarget(
        target: PyTargetExpression,
        resolveCtx: PyResolveContext
    ): Pair<String, Pair<String?, PsiNamedElement?>>? {
        // 1) Look into assigned value:   x: T = field(alias="...") / Field(alias="...")
        when (val value = target.findAssignedValue()) {
            is PyCallExpression -> {
                val calleeName = (value.callee as? PyQualifiedExpression)?.referencedName
                if (calleeName == "field" || calleeName == "Field") {
                    val aliasKw = value.getKeywordArgument("alias")
                    val nameKw = value.getKeywordArgument("name")
                    val aliasText = stringValueOf(aliasKw) ?: stringValueOf(nameKw)
                    val aliasRef = aliasKw?.let { resolveFirstNamed(it, resolveCtx) }
                    if (aliasText != null || aliasRef != null) {
                        val effectiveName = aliasText ?: target.name ?: return null
                        return effectiveName to (aliasText to aliasRef)
                    }
                }
            }
        }

        // 2) Look into type annotation:   x: Annotated[T, Alias("...") or SomeName]
        target.annotation?.value?.let { anno ->
            when (anno) {
                is PySubscriptionExpression -> {
                    val qname = (anno.operand as? PyQualifiedExpression)?.referencedName
                    if (qname == "Annotated") {
                        val indices = (anno.indexExpression as? PyTupleExpression)?.elements.orEmpty()
                        // conventionally: Annotated[T, meta...]
                        indices.drop(1).forEach { meta ->
                            // Annotated[T, Alias("foo")] or Annotated[T, "foo"]
                            when (meta) {
                                is PyCallExpression -> {
                                    val callee = (meta.callee as? PyQualifiedExpression)?.referencedName
                                    if (callee == "Alias" || callee == "alias") {
                                        val arg = meta.arguments.firstOrNull()
                                        val aliasText = stringValueOf(arg)
                                        val aliasRef = arg?.let { resolveFirstNamed(it, resolveCtx) }
                                        if (aliasText != null || aliasRef != null) {
                                            val effectiveName = aliasText ?: target.name ?: return null
                                            return effectiveName to (aliasText to aliasRef)
                                        }
                                    }
                                }

                                is PyStringLiteralExpression -> {
                                    val text = meta.stringValue
                                    return text to (text to null)
                                }

                                is PyReferenceExpression -> {
                                    val resolved = meta.getReference(resolveCtx)?.multiResolve(false)
                                        ?.firstOrNull()?.element as? PsiNamedElement
                                    if (resolved != null) {
                                        val effectiveName = resolved.name ?: target.name ?: return null
                                        return effectiveName to (resolved.name to resolved)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun stringValueOf(element: PsiElement?): String? = when (element) {
        is PyStringLiteralExpression -> element.stringValue
        is PyKeywordArgument -> stringValueOf(element.valueExpression)
        is PyReferenceExpression -> element.name // keep simple; resolution handled separately
        else -> null
    }

    private fun resolveFirstNamed(element: PsiElement, resolveCtx: PyResolveContext): PsiNamedElement? =
        (element as? PyReferenceExpression)
            ?.getReference(resolveCtx)
            ?.multiResolve(false)
            ?.firstOrNull()
            ?.element as? PsiNamedElement

    data class FieldSpec(
        val name: String,
        val type: PyType?,
        val aliasName: String?,
        val aliasElement: PsiNamedElement? = null
    )
}