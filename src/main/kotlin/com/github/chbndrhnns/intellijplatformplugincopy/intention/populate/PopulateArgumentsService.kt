package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.isDataclassClass
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.isPositionalOnlyCallable
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.PyImportService
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.resolveDataclassFieldParameters
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*

/**
 * Service that provides shared logic for populating call arguments.
 */
class PopulateArgumentsService {

    private val imports: PyImportService = PyImportService()

    /**
     * Finds the PyCallExpression at the current caret position.
     */
    fun findCallExpression(editor: Editor, file: PyFile): PyCallExpression? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null
        val call =
            PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java, /* strict = */ false) ?: return null

        val argumentList = call.argumentList ?: return null
        val textRange = argumentList.textRange ?: return null

        if (offset > textRange.startOffset && offset < textRange.endOffset) {
            return call
        }
        return null
    }

    /**
     * Returns all missing parameters for the given call expression.
     */
    fun getMissingParameters(call: PyCallExpression, context: TypeEvalContext): List<PyCallableParameter> {
        val resolveContext = PyResolveContext.defaultContext(context)
        val mappings = call.multiMapArguments(resolveContext)
        if (mappings.isEmpty()) return emptyList()

        val mapping = mappings.first()
        val callableType: PyCallableType = mapping.callableType ?: return emptyList()

        val allParams = callableType.getParameters(context) ?: return emptyList()
        val mapped = mapping.mappedParameters

        return allParams
            .asSequence()
            .filter { !it.isSelf }
            .filter { !it.isPositionalContainer && !it.isKeywordContainer }
            .filter { param -> !mapped.values.contains(param) }
            .filter { it.name != null }
            .filter { !it.name!!.startsWith("_") }
            .toList()
    }

    /**
     * Returns only missing required parameters (those without default values).
     */
    fun getMissingRequiredParameters(call: PyCallExpression, context: TypeEvalContext): List<PyCallableParameter> {
        return getMissingParameters(call, context).filter { !it.hasDefaultValue() }
    }

    /**
     * Checks if the intention should be available for the given call.
     */
    fun isAvailable(call: PyCallExpression, context: TypeEvalContext): Boolean {
        if (isPositionalOnlyCallable(call)) return false
        return getMissingParameters(call, context).isNotEmpty()
    }

    /**
     * Checks if recursive mode is applicable (i.e., any missing parameter has a dataclass type).
     */
    fun isRecursiveApplicable(call: PyCallExpression, context: TypeEvalContext): Boolean {
        val missing = getMissingParameters(call, context)
        return missing.any { param ->
            val type = param.getType(context)
            hasDataclassType(type, context)
        }
    }

    private fun hasDataclassType(type: PyType?, context: TypeEvalContext): Boolean {
        return when (type) {
            is PyClassType -> isDataclassClass(type.pyClass)
            is PyUnionType -> type.members.any { hasDataclassType(it, context) }
            else -> false
        }
    }

    /**
     * Populates the call with missing arguments based on the given options.
     */
    fun populateArguments(
        project: Project,
        file: PyFile,
        call: PyCallExpression,
        options: PopulateOptions,
        context: TypeEvalContext
    ) {
        val missing = when (options.mode) {
            PopulateMode.ALL -> getMissingParameters(call, context)
            PopulateMode.REQUIRED_ONLY -> getMissingRequiredParameters(call, context)
        }
        if (missing.isEmpty()) return

        val generator = PyElementGenerator.getInstance(project)
        val argumentList = call.argumentList ?: return
        val languageLevel = LanguageLevel.forElement(file)

        // Always use the recursive generator which handles both nested dataclasses and leaf alias wrapping.
        populateRecursively(project, file, call, argumentList, missing, context, generator, languageLevel)
    }

    private fun populateSimple(
        argumentList: PyArgumentList,
        missing: List<PyCallableParameter>,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ) {
        for (param in missing) {
            val name = param.name ?: continue
            val arg: PyKeywordArgument = generator.createKeywordArgument(languageLevel, name, "...")
            argumentList.addArgument(arg)
        }
    }

    private fun populateRecursively(
        project: Project,
        file: PyFile,
        call: PyCallExpression,
        argumentList: PyArgumentList,
        missing: List<PyCallableParameter>,
        context: TypeEvalContext,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ) {
        val calleeClass: PyClass? = (call.callee as? PyReferenceExpression)
            ?.reference
            ?.resolve()
            ?.let { it as? PyClass }

        val paramData = missing.mapNotNull { param ->
            val name = param.name ?: return@mapNotNull null
            val type = param.getType(context)
            var result = generateValue(type, context, 0, generator, languageLevel)

            // If we only have a leaf "..." and the dataclass field annotation is an alias
            // (e.g. NewType), prefer wrapping with that alias at the top level too.
            if (result.text == DEFAULT_FALLBACK_VALUE && calleeClass != null) {
                val field = calleeClass.findClassAttribute(name, true, context)
                val aliasName = (field?.annotation?.value as? PyReferenceExpression)?.name
                if (!aliasName.isNullOrBlank() && !isBuiltinName(aliasName)) {
                    result = GenerationResult("$aliasName(...)", emptySet())
                }
            }

            Triple(param, name, result)
        }

        // First, create placeholder keyword arguments for all missing params
        for ((_, name, _) in paramData) {
            val kwArg = generator.createKeywordArgument(languageLevel, name, "None")
            argumentList.addArgument(kwArg)
        }

        // Then, replace placeholder values and ensure imports
        for ((_, name, result) in paramData) {
            val kwArg = argumentList.arguments.filterIsInstance<PyKeywordArgument>().find { it.keyword == name }
            if (kwArg != null) {
                val valueExpr = generator.createExpressionFromText(languageLevel, result.text)
                val replaced = kwArg.valueExpression?.replace(valueExpr) as? PyExpression ?: continue

                // Ensure imports for all classes used in generation
                for (cls in result.imports) {
                    imports.ensureImportedIfNeeded(file, replaced, cls)
                }
            }
        }

        CodeStyleManager.getInstance(project).reformat(argumentList)
    }

    private fun generateValue(
        type: PyType?,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ): GenerationResult {
        if (depth > MAX_RECURSION_DEPTH) return GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())
        if (type == null) return GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())

        return when (type) {
            is PyUnionType -> generateUnionValue(type, context, depth, generator, languageLevel)
            is PyClassType -> {
                if (isDataclassClass(type.pyClass)) {
                    generateDataclassValue(type.pyClass, context, depth, generator, languageLevel)
                } else {
                    // Some providers represent typing.NewType/aliases as PyClassType but without a backing PyClass.
                    val aliasLike = generateAliasFromClassType(type)
                    aliasLike ?: GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())
                }
            }

            is PyClassLikeType -> generateAliasOrNewTypeValue(type)
            else -> GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())
        }
    }

    private fun generateUnionValue(
        type: PyUnionType,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ): GenerationResult {
        return type.members.find { memberType ->
            (memberType as? PyClassType)?.pyClass?.let { isDataclassClass(it) } == true
        }?.let { generateValue(it, context, depth, generator, languageLevel) }
            ?: GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())
    }

    private fun generateDataclassValue(
        pyClass: PyClass,
        context: TypeEvalContext,
        depth: Int,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ): GenerationResult {
        val className = pyClass.name ?: return GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())
        val callExpr = generator.createExpressionFromText(languageLevel, "$className()") as? PyCallExpression
        val argumentList = callExpr?.argumentList ?: return GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())

        val requiredImports = mutableSetOf<PyClass>()
        requiredImports.add(pyClass)

        extractDataclassFields(pyClass, context).forEach { field ->
            val result = generateValue(field.type, context, depth + 1, generator, languageLevel)
            var valStr = result.text

            // If generation fell back to ellipsis for a leaf, but the annotation is an alias-like
            // (e.g., NewType), prefer wrapping with that alias to produce Alias(...).
            if (valStr == DEFAULT_FALLBACK_VALUE && !field.aliasName.isNullOrBlank()) {
                val alias = field.aliasName
                if (!isBuiltinName(alias!!)) {
                    valStr = "$alias(...)"
                }
            }

            requiredImports.addAll(result.imports)

            val kwArg = generator.createKeywordArgument(languageLevel, field.name, valStr)
            argumentList.addArgument(kwArg)
        }

        return if (argumentList.arguments.isNotEmpty())
            GenerationResult(callExpr!!.text, requiredImports)
        else
            GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())
    }

    private fun extractDataclassFields(
        pyClass: PyClass, context: TypeEvalContext
    ): List<FieldSpec> {
        val fields = mutableListOf<FieldSpec>()

        val initMethod = pyClass.findInitOrNew(false, context)
        if (initMethod != null) {
            val callableType = context.getType(initMethod) as? PyCallableType
            val params = callableType?.getParameters(context)
            params?.filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }?.forEach { param ->
                var name = param.name ?: return@forEach
                val field = pyClass.findClassAttribute(name, true, context)
                if (field != null) {
                    name = resolveFieldAlias(pyClass, field, context, name)
                }
                val aliasName = field?.annotation?.value?.let { (it as? PyReferenceExpression)?.name }
                fields.add(FieldSpec(name, param.getType(context), aliasName))
            }
        } else {
            // Fallback for synthetic __init__
            pyClass.classAttributes.forEach { attr ->
                if (attr.annotation != null) {
                    var name = attr.name ?: return@forEach
                    name = resolveFieldAlias(pyClass, attr, context, name)
                    val aliasName = (attr.annotation?.value as? PyReferenceExpression)?.name
                    fields.add(FieldSpec(name, context.getType(attr), aliasName))
                }
            }
        }
        return fields
    }

    private fun resolveFieldAlias(
        pyClass: PyClass, field: PyTargetExpression, context: TypeEvalContext, originalName: String
    ): String {
        val dataclassParams = parseDataclassParameters(pyClass, context) ?: return originalName
        val fieldParams = resolveDataclassFieldParameters(pyClass, dataclassParams, field, context)
        return fieldParams?.alias ?: originalName
    }

    companion object {
        private const val MAX_RECURSION_DEPTH = 5
        private const val DEFAULT_FALLBACK_VALUE = "..."
    }

    private data class GenerationResult(
        val text: String,
        val imports: Set<PyClass>
    )

    /**
     * For alias-like types (typing.NewType, typing.TypeAlias, forward refs resolved to class-like without a PyClass),
     * generate a constructor-like call `Name(...)` so leaves are wrapped with an instance placeholder.
     * Falls back to `...` for builtin types and unknown names.
     */
    private fun generateAliasOrNewTypeValue(type: PyClassLikeType): GenerationResult {
        // Avoid builtins and sentinel-like names
        val name = type.name ?: type.classQName?.substringAfterLast('.')
        if (name.isNullOrBlank()) return GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())

        if (isBuiltinName(name)) return GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())

        // Prefer using the simple name; imports are not required for aliases defined in the same file.
        return GenerationResult("$name(...)", emptySet())
    }

    private fun generateAliasFromClassType(type: PyClassType): GenerationResult? {
        // If there is a real class behind it (e.g., builtin or user class), we don't treat it as alias here.
        if (type.pyClass != null) return null

        val name = type.name ?: type.classQName?.substringAfterLast('.')
        if (name.isNullOrBlank()) return null

        if (isBuiltinName(name)) return null

        return GenerationResult("$name(...)", emptySet())
    }

    private fun isBuiltinName(name: String): Boolean {
        val lower = name.lowercase()
        return lower in setOf(
            "int", "str", "float", "bool", "bytes", "list", "dict", "set", "tuple", "range", "complex"
        )
    }

    private data class FieldSpec(val name: String, val type: PyType?, val aliasName: String?)
}
