package com.github.chbndrhnns.betterpy.features.intentions.populate

import com.github.chbndrhnns.betterpy.features.intentions.customtype.isDataclassClass
import com.github.chbndrhnns.betterpy.features.intentions.shared.PyBuiltinNames
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
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
        scopeOwner: ScopeOwner,
        unionSelections: Map<String, String> = emptyMap(),
        useConstructors: Boolean = false
    ): GenerationResult {
        if (depth > MAX_RECURSION_DEPTH || type == null) return defaultResult()

        return when (type) {
            is PyUnionType -> generateUnionValue(
                type,
                context,
                depth,
                generator,
                languageLevel,
                scopeOwner,
                unionSelections,
                useConstructors
            )

            is PyTupleType -> generateTupleValue(
                type,
                context,
                depth,
                generator,
                languageLevel,
                scopeOwner,
                unionSelections,
                useConstructors
            )

            is PyCollectionType -> generateCollectionValue(
                type,
                context,
                depth,
                generator,
                languageLevel,
                scopeOwner,
                unionSelections,
                useConstructors
            )

            is PyClassType -> generateClassTypeValue(
                type,
                context,
                depth,
                generator,
                languageLevel,
                scopeOwner,
                unionSelections,
                useConstructors
            )
            is PyClassLikeType -> generateAliasLikeValue(type, context, scopeOwner)
            else -> defaultResult()
        }
    }

    private fun generateTupleValue(
        type: PyTupleType,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner,
        unionSelections: Map<String, String>,
        useConstructors: Boolean
    ): GenerationResult {
        val elements = type.elementTypes
        if (elements.isEmpty()) return GenerationResult("()", emptySet())

        val results = elements.map {
            generateValue(
                it,
                context,
                depth + 1,
                generator,
                languageLevel,
                scopeOwner,
                unionSelections,
                useConstructors
            )
        }
        
        val imports = results.flatMap { it.imports }.toSet()
        
        if (elements.size == 1) {
            return GenerationResult("(${results[0].text},)", imports)
        }
        
        val text = results.joinToString(prefix = "(", postfix = ")", separator = ", ") { it.text }
        return GenerationResult(text, imports)
    }

    private fun generateClassTypeValue(
        type: PyClassType,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner,
        unionSelections: Map<String, String>,
        useConstructors: Boolean
    ): GenerationResult {
        val pyClass = type.pyClass
        if (isDataclassClass(pyClass)) {
            return generateDataclassValue(
                pyClass,
                context,
                depth,
                generator,
                languageLevel,
                scopeOwner,
                unionSelections,
                useConstructors
            )
        }

        // Try alias logic first (e.g. NewType)
        val aliasResult = generateAliasFromClassType(type, context, scopeOwner)
        if (aliasResult != null) return aliasResult

        val hasRequiredArgs = hasRequiredConstructorArgs(pyClass, context)

        // For builtin types (int, str, float, bool, etc.), prefer constructors when enabled.
        val name = pyClass.name
        if (name != null && isBuiltinOrSubclass(pyClass, context, scopeOwner)) {
            return if (useConstructors) GenerationResult("$name()", emptySet()) else defaultResult()
        }

        if (useConstructors && hasRequiredArgs) {
            return defaultResult()
        }

        // Check if imported as alias
        val file = scopeOwner.containingFile as? PyFile
        if (file != null) {
            for (stmt in file.importBlock) {
                if (stmt is PyFromImportStatement) {
                    for (elt in stmt.importElements) {
                        if (elt.asName != null) {
                            val result = elt.multiResolve()
                            val resolved = result.firstOrNull()?.element as? PyClass

                            if (resolved != null && resolved.isEquivalentTo(pyClass)) {
                                val alias = elt.visibleName
                                if (alias != null) {
                                    return GenerationResult("$alias()", setOf(pyClass))
                                }
                            }
                        }
                    }
                } else if (stmt is PyImportStatement) {
                    for (elt in stmt.importElements) {
                        if (elt.asName != null) {
                            val modQName = elt.importedQName
                            val classQName = pyClass.qualifiedName
                            if (modQName != null && classQName != null && name != null) {
                                if (classQName == modQName.append(name).toString()) {
                                    val alias = elt.visibleName
                                    if (alias != null) {
                                        return GenerationResult("$alias.$name()", setOf(pyClass))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fallback for regular (non-builtin) classes: generate constructor call "ClassName()"
        // This satisfies the requirement "pre-populate it with a single expected item type with an empty constructor call"
        if (name != null) {
            return GenerationResult("$name()", setOf(pyClass))
        }

        return defaultResult()
    }

    private fun hasRequiredConstructorArgs(pyClass: PyClass, context: TypeEvalContext): Boolean {
        val init = pyClass.findInitOrNew(true, context) ?: return false
        val callable = context.getType(init) as? PyCallableType ?: return false
        val params = callable.getParameters(context) ?: return false
        return params.any { param ->
            !param.isSelf &&
                    !param.isPositionalContainer &&
                    !param.isKeywordContainer &&
                    !param.hasDefaultValue()
        }
    }

    private fun isBuiltinOrSubclass(pyClass: PyClass, context: TypeEvalContext, scopeOwner: ScopeOwner): Boolean {
        val cache = PyBuiltinCache.getInstance(scopeOwner)
        if (cache.isBuiltin(pyClass)) return true
        for (name in PyBuiltinNames.names()) {
            val builtin = cache.getClass(name) ?: continue
            if (pyClass.isSubclass(builtin, context)) return true
        }
        return false
    }

    private fun generateCollectionValue(
        type: PyCollectionType,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner,
        unionSelections: Map<String, String>,
        useConstructors: Boolean
    ): GenerationResult {
        val normalized = type.name?.let(::normalizeName) ?: return defaultResult()
        
        // Handle Dict
        if (normalized == PyNames.DICT) {
             val keyType = type.elementTypes.getOrNull(0)
             val valType = type.elementTypes.getOrNull(1)
             // If generic dict without args, fallback
             if (keyType == null && valType == null) return defaultResult()

            val keyResult =
                generateValue(
                    keyType,
                    context,
                    depth + 1,
                    generator,
                    languageLevel,
                    scopeOwner,
                    unionSelections,
                    useConstructors
                )
            val valResult =
                generateValue(
                    valType,
                    context,
                    depth + 1,
                    generator,
                    languageLevel,
                    scopeOwner,
                    unionSelections,
                    useConstructors
                )
             
             return GenerationResult("{${keyResult.text}: ${valResult.text}}", keyResult.imports + valResult.imports)
        }

        val elemType = type.elementTypes.firstOrNull() ?: return defaultResult()
        val elemResult =
            generateValue(
                elemType,
                context,
                depth + 1,
                generator,
                languageLevel,
                scopeOwner,
                unionSelections,
                useConstructors
            )
        
        val text = when (normalized) {
            "list" -> "[${elemResult.text}]"
            PyNames.SET -> "{${elemResult.text}}"
            PyNames.TUPLE -> "(${elemResult.text})"
            else -> return defaultResult()
        }
        return GenerationResult(text, elemResult.imports)
    }

    private fun generateUnionValue(
        type: PyUnionType,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner,
        unionSelections: Map<String, String>,
        useConstructors: Boolean
    ): GenerationResult {
        val members = normalizeUnionMembers(type)
        if (members.isEmpty()) return defaultResult()

        val signature = unionSignature(members)
        val preferredName = unionSelections[signature]
        val ranked = rankUnionMembers(members)
        val chosen = preferredName?.let { name ->
            ranked.firstOrNull { renderTypeName(it) == name }
        } ?: pickDefaultUnionMember(ranked)

        // Union member selection happens here; chooser overrides flow via unionSelections.
        return chosen?.let {
            generateValue(
                it,
                context,
                depth,
                generator,
                languageLevel,
                scopeOwner,
                unionSelections,
                useConstructors
            )
        } ?: defaultResult()
    }

    private fun generateDataclassValue(
        pyClass: PyClass,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner,
        unionSelections: Map<String, String>,
        useConstructors: Boolean
    ): GenerationResult {
        val className = pyClass.name ?: return defaultResult()
        val call = generator.createExpressionFromText(languageLevel, "$className()") as? PyCallExpression
            ?: return defaultResult()
        val argList = call.argumentList ?: return defaultResult()

        val requiredImports = linkedSetOf<PsiNamedElement>(pyClass)

        for (field in fieldExtractor.extractDataclassFields(pyClass, context)) {
            val fieldResult =
                generateValue(
                    field.type,
                    context,
                    depth + 1,
                    generator,
                    languageLevel,
                    scopeOwner,
                    unionSelections,
                    useConstructors
                )
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

    internal fun normalizeUnionMembers(union: PyUnionType): List<PyType> {
        val flattened = mutableListOf<PyType>()
        fun flatten(t: PyType) {
            if (t is PyUnionType) {
                t.members.filterNotNull().forEach { member -> flatten(member) }
            } else {
                flattened.add(t)
            }
        }
        flatten(union)

        val seen = mutableSetOf<String>()
        return flattened.filter { member ->
            val key = renderTypeName(member)
            if (seen.contains(key)) false else {
                seen.add(key)
                true
            }
        }
    }

    internal fun unionSignature(members: List<PyType>): String {
        return members.map(::renderTypeName).sorted().joinToString("|")
    }

    internal fun renderTypeName(type: PyType): String {
        if (isNoneType(type)) return "None"
        val classQName = (type as? PyClassType)?.classQName ?: (type as? PyClassLikeType)?.classQName
        return classQName ?: type.name ?: type.toString()
    }

    internal fun renderUnionChoiceLabel(type: PyType): String {
        val name = type.name ?: renderTypeName(type)
        val qName = (type as? PyClassType)?.classQName ?: (type as? PyClassLikeType)?.classQName
        return if (!qName.isNullOrBlank() && qName != name) "$name ($qName)" else name
    }

    internal fun rankUnionMembers(members: List<PyType>): List<PyType> {
        return members.sortedWith(compareBy({ kindOrder.indexOf(kindOf(it)) }, { renderTypeName(it) }))
    }

    internal fun isNoneType(type: PyType): Boolean {
        return type.name == "None" || type.name == "NoneType"
    }

    private fun pickDefaultUnionMember(members: List<PyType>): PyType? {
        val nonNone = members.filterNot(::isNoneType)
        return if (nonNone.isNotEmpty()) nonNone.first() else members.firstOrNull()
    }

    private enum class Kind {
        DATA_MODEL,
        COLLECTION,
        CLASS,
        PRIMITIVE,
        LITERAL,
        ANYLIKE,
        NONE
    }

    private fun kindOf(type: PyType): Kind {
        return when {
            isNoneType(type) -> Kind.NONE
            type is PyClassType && isDataclassClass(type.pyClass) -> Kind.DATA_MODEL
            type is PyCollectionType && type.elementTypes.isNotEmpty() -> Kind.COLLECTION
            type is PyClassType || type is PyClassLikeType -> Kind.CLASS
            type.name in setOf("str", "int", "float", "bool", "bytes") -> Kind.PRIMITIVE
            type.name?.startsWith("Literal") == true -> Kind.LITERAL
            type.name in setOf("Any", "Never", "NoReturn") -> Kind.ANYLIKE
            else -> Kind.CLASS
        }
    }

    private val kindOrder = listOf(
        Kind.DATA_MODEL,
        Kind.COLLECTION,
        Kind.CLASS,
        Kind.PRIMITIVE,
        Kind.LITERAL,
        Kind.ANYLIKE,
        Kind.NONE
    )

    private fun normalizeName(name: String): String {
        // normalize `List`/`Set` to builtin lowercase to reduce branching
        return when (name) {
            "List", "list" -> "list"
            "Set", "set" -> PyNames.SET
            "Tuple", "tuple" -> PyNames.TUPLE
            "Dict", "dict" -> PyNames.DICT
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
