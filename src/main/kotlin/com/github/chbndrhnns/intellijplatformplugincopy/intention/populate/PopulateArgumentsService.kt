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
 * Extracted from PopulateKwOnlyArgumentsIntention, PopulateRequiredArgumentsIntention,
 * and PopulateRecursiveArgumentsIntention.
 */
class PopulateArgumentsService {

    private val imports: PyImportService = PyImportService()

    /**
     * Finds the PyCallExpression at the current caret position.
     */
    fun findCallExpression(editor: Editor, file: PyFile): PyCallExpression? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java, /* strict = */ false)
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

        if (options.recursive) {
            populateRecursively(project, file, argumentList, missing, context, generator, languageLevel)
        } else {
            populateSimple(argumentList, missing, generator, languageLevel)
        }
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
        argumentList: PyArgumentList,
        missing: List<PyCallableParameter>,
        context: TypeEvalContext,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ) {
        val paramData = missing.mapNotNull { param ->
            val name = param.name ?: return@mapNotNull null
            val type = param.getType(context)
            val result = generateValue(type, context, 0, generator, languageLevel)
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
                    GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())
                }
            }
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

        extractDataclassFields(pyClass, context).forEach { (name, fieldType) ->
            val result = generateValue(fieldType, context, depth + 1, generator, languageLevel)
            val valStr = result.text
            requiredImports.addAll(result.imports)

            val kwArg = generator.createKeywordArgument(languageLevel, name, valStr)
            argumentList.addArgument(kwArg)
        }

        return if (argumentList.arguments.isNotEmpty())
            GenerationResult(callExpr!!.text, requiredImports)
        else
            GenerationResult(DEFAULT_FALLBACK_VALUE, emptySet())
    }

    private fun extractDataclassFields(
        pyClass: PyClass, context: TypeEvalContext
    ): List<Pair<String, PyType?>> {
        val fields = mutableListOf<Pair<String, PyType?>>()

        val initMethod = pyClass.findInitOrNew(false, context)
        if (initMethod != null) {
            val callableType = context.getType(initMethod) as? PyCallableType
            val params = callableType?.getParameters(context)
            if (params != null) {
                params.filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }.forEach { param ->
                    var name = param.name ?: return@forEach
                    val field = pyClass.findClassAttribute(name, true, context) as? PyTargetExpression
                    if (field != null) {
                        name = resolveFieldAlias(pyClass, field, context, name)
                    }
                    fields.add(name to param.getType(context))
                }
            }
        } else {
            // Fallback for synthetic __init__
            pyClass.classAttributes.forEach { attr ->
                if (attr.annotation != null) {
                    var name = attr.name ?: return@forEach
                    name = resolveFieldAlias(pyClass, attr, context, name)
                    fields.add(name to context.getType(attr))
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
}
