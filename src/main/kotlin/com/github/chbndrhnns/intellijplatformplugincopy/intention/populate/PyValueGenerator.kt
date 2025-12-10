package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.isDataclassClass
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyBuiltinNames
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.*

/**
 * Generates placeholder values for different Python types.
 */
class PyValueGenerator(private val fieldExtractor: PyDataclassFieldExtractor) {

    data class GenerationResult(val text: String, val imports: Set<PsiNamedElement>)

    fun generateValue(
        type: PyType?,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner
    ): GenerationResult {
        if (depth > MAX_RECURSION_DEPTH || type == null) return defaultResult()

        return when (type) {
            is PyUnionType -> generateUnionValue(type, context, depth, generator, languageLevel, scopeOwner)
            is PyCollectionType -> generateCollectionValue(type, context, depth, generator, languageLevel, scopeOwner)
            is PyClassType -> generateClassTypeValue(type, context, depth, generator, languageLevel, scopeOwner)
            is PyClassLikeType -> generateAliasLikeValue(type, context, scopeOwner)
            else -> defaultResult()
        }
    }

    private fun generateClassTypeValue(
        type: PyClassType,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner
    ): GenerationResult {
        val pyClass = type.pyClass
        return if (isDataclassClass(pyClass)) {
            generateDataclassValue(pyClass, context, depth, generator, languageLevel, scopeOwner)
        } else {
            // Some providers represent typing.NewType/aliases as PyClassType but without a backing PyClass.
            generateAliasFromClassType(type, context, scopeOwner) ?: defaultResult()
        }
    }

    private fun generateCollectionValue(
        type: PyCollectionType,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner
    ): GenerationResult {
        val normalized = type.name?.let(::normalizeName) ?: return defaultResult()
        val elemType = type.elementTypes.firstOrNull() ?: return defaultResult()

        val elemResult = generateValue(elemType, context, depth + 1, generator, languageLevel, scopeOwner)
        val text = when (normalized) {
            "list" -> "[${elemResult.text}]"
            PyNames.SET -> "{${elemResult.text}}"
            else -> return defaultResult() // extend for tuple/dict if needed
        }
        return GenerationResult(text, elemResult.imports)
    }

    private fun generateUnionValue(
        type: PyUnionType,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner
    ): GenerationResult {
        // Prefer a dataclass alternative inside a union
        val member = type.members.firstOrNull { m ->
            (m as? PyClassType)?.pyClass?.let { isDataclassClass(it) } == true
        } ?: return defaultResult()

        return generateValue(member, context, depth, generator, languageLevel, scopeOwner)
    }

    private fun generateDataclassValue(
        pyClass: PyClass,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner
    ): GenerationResult {
        val className = pyClass.name ?: return defaultResult()
        val call = generator.createExpressionFromText(languageLevel, "$className()") as? PyCallExpression
            ?: return defaultResult()
        val argList = call.argumentList ?: return defaultResult()

        val requiredImports = linkedSetOf<PsiNamedElement>(pyClass)

        for (field in fieldExtractor.extractDataclassFields(pyClass, context)) {
            val fieldResult = generateValue(field.type, context, depth + 1, generator, languageLevel, scopeOwner)
            var valueText = fieldResult.text

            // If a leaf fell back to ellipsis and the annotation is alias-like (a resolvable symbol),
            // prefer `Alias(...)`. Do not wrap plain string aliases like Pydantic's Field(alias="...").
            if (valueText == DEFAULT_FALLBACK_VALUE && field.aliasElement != null) {
                val alias = field.aliasName
                if (!alias.isNullOrBlank() && !PyBuiltinNames.isBuiltin(alias)) {
                    valueText = "$alias(...)"
                    // aliasElement is non-null here; add it to imports if applicable
                    requiredImports.add(field.aliasElement)
                }
            }

            requiredImports.addAll(fieldResult.imports)
            argList.addArgument(generator.createKeywordArgument(languageLevel, field.name, valueText))
        }

        return if (argList.arguments.isNotEmpty()) GenerationResult(call.text, requiredImports) else defaultResult()
    }

    /**
     * For alias-like types (typing.NewType, typing.TypeAlias, forward refs resolved to class-like without a PyClass),
     * generate a constructor-like call `Name(...)` so leaves are wrapped with an instance placeholder.
     * Falls back to `...` for builtin types and unknown names.
     */
    private fun generateAliasLikeValue(
        type: PyClassLikeType,
        context: TypeEvalContext,
        scopeOwner: ScopeOwner
    ): GenerationResult {
        val name = type.name ?: type.classQName?.substringAfterLast('.')
        val qName = type.classQName
        val resolved = if (qName != null) resolveInScope(qName, scopeOwner, context) else null
        return wrapAliasNameOrDefault(name, resolved)
    }

    private fun generateAliasFromClassType(
        type: PyClassType,
        context: TypeEvalContext,
        scopeOwner: ScopeOwner
    ): GenerationResult? {
        val name = type.name ?: type.classQName?.substringAfterLast('.') ?: return null
        val pyClass = type.pyClass
        // If there is a real class and its simple name equals the alias name, do not treat as alias.
        if (pyClass?.name == name) return null

        val qName = type.classQName
        val resolved = if (qName != null) resolveInScope(qName, scopeOwner, context) else null

        return wrapAliasNameOrDefault(name, resolved).takeUnless { it === DEFAULT_RESULT_OBJ }
    }

    private fun wrapAliasNameOrDefault(name: String?, element: PsiNamedElement? = null): GenerationResult {
        val simple = name?.takeIf { it.isNotBlank() } ?: return defaultResult()
        if (PyBuiltinNames.isBuiltin(simple)) return defaultResult()
        val imports = if (element != null) setOf(element) else emptySet()
        return GenerationResult("$simple(...)", imports)
    }

    private fun resolveInScope(qName: String, scopeOwner: ScopeOwner, context: TypeEvalContext): PsiNamedElement? {
        val qualifiedName = QualifiedName.fromDottedString(qName)
        val resolved = PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, scopeOwner, context)
        return resolved.firstOrNull() as? PsiNamedElement
    }

    // --- Utilities -------------------------------------------------------------------------------

    private fun normalizeName(name: String): String {
        // normalize `List`/`Set` to builtin lowercase to reduce branching
        return when (name) {
            "List", "list" -> "list"
            "Set", "set" -> PyNames.SET
            else -> name
        }
    }

    private fun defaultResult() = DEFAULT_RESULT_OBJ

    companion object {
        private const val MAX_RECURSION_DEPTH = 5
        private const val DEFAULT_FALLBACK_VALUE = "..."
        private val DEFAULT_RESULT_OBJ = GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())
    }
}