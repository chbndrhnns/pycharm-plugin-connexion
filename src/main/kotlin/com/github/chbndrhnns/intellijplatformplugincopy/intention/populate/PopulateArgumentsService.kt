package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyBuiltinNames
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.PyImportService
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Service that orchestrates the population of call arguments with placeholder values.
 * Delegates specific responsibilities to focused helper classes.
 */
class PopulateArgumentsService {

    private val imports: PyImportService = PyImportService()
    private val callFinder = PyCallExpressionFinder()
    private val parameterAnalyzer = PyParameterAnalyzer()
    private val fieldExtractor = PyDataclassFieldExtractor()
    private val valueGenerator = PyValueGenerator(fieldExtractor)

    /**
     * Finds the PyCallExpression at the current caret position.
     */
    fun findCallExpression(editor: Editor, file: PyFile): PyCallExpression? {
        return callFinder.findCallExpression(editor, file)
    }

    /**
     * Returns all missing parameters for the given call expression.
     */
    fun getMissingParameters(call: PyCallExpression, context: TypeEvalContext): List<PyCallableParameter> {
        return parameterAnalyzer.getMissingParameters(call, context)
    }

    /**
     * Returns only missing required parameters (those without default values).
     */
    fun getMissingRequiredParameters(call: PyCallExpression, context: TypeEvalContext): List<PyCallableParameter> {
        return parameterAnalyzer.getMissingRequiredParameters(call, context)
    }

    /**
     * Checks if the intention should be available for the given call.
     */
    fun isAvailable(call: PyCallExpression, context: TypeEvalContext): Boolean {
        return parameterAnalyzer.isAvailable(call, context)
    }

    /**
     * Checks if recursive mode is applicable (i.e., any missing parameter has a dataclass type).
     */
    fun isRecursiveApplicable(call: PyCallExpression, context: TypeEvalContext): Boolean {
        return parameterAnalyzer.isRecursiveApplicable(call, context)
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
        populateRecursively(project, file, call, argumentList, missing, context, generator, languageLevel, options)
    }

    private fun populateRecursively(
        project: Project,
        file: PyFile,
        call: PyCallExpression,
        argumentList: PyArgumentList,
        missing: List<PyCallableParameter>,
        context: TypeEvalContext,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        options: PopulateOptions
    ) {
        val calleeClass: PyClass? = (call.callee as? PyReferenceExpression)
            ?.reference
            ?.resolve()
            ?.let { it as? PyClass }

        val paramData = missing.mapNotNull { param ->
            val name = param.name ?: return@mapNotNull null
            val type = param.getType(context)

            val result: PyValueGenerator.GenerationResult = if (options.useLocalScope) {
                val owner = ScopeUtil.getScopeOwner(call) ?: file
                val qName = QualifiedName.fromDottedString(name)
                val resolved = PyResolveUtil.resolveQualifiedNameInScope(qName, owner, context)

                var found = resolved.isNotEmpty()
                if (!found) {
                    val scope = ControlFlowCache.getScope(owner)
                    if (scope.containsDeclaration(name)) {
                        found = true
                    }
                }

                if (found) {
                    PyValueGenerator.GenerationResult(name, emptySet())
                } else {
                    valueGenerator.generateValue(type, context, 0, generator, languageLevel)
                }
            } else {
                valueGenerator.generateValue(type, context, 0, generator, languageLevel)
            }

            // If we only have a leaf "..." and the dataclass field annotation is an alias
            // (e.g. NewType), prefer wrapping with that alias at the top level too.
            var finalResult = result
            if (finalResult.text == "..." && calleeClass != null) {
                val field = calleeClass.findClassAttribute(name, true, context)
                val aliasName = (field?.annotation?.value as? PyReferenceExpression)?.name
                if (!aliasName.isNullOrBlank() && !PyBuiltinNames.isBuiltin(aliasName)) {
                    finalResult = PyValueGenerator.GenerationResult("$aliasName(...)", emptySet())
                }
            }

            Triple(param, name, finalResult)
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

        if (call !is PyDecorator && call.parent !is PyDecorator) {
            CodeStyleManager.getInstance(project).reformat(argumentList)
        }
    }
}
