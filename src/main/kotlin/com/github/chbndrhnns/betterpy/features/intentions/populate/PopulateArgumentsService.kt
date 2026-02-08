package com.github.chbndrhnns.betterpy.features.intentions.populate

import com.github.chbndrhnns.betterpy.core.psi.PyImportService
import com.github.chbndrhnns.betterpy.features.intentions.shared.PopupHost
import com.github.chbndrhnns.betterpy.features.intentions.shared.PyBuiltinNames
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
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
        context: TypeEvalContext,
        unionSelections: Map<String, String> = emptyMap()
    ) {
        val missing = missingParametersFor(call, options, context)
        if (missing.isEmpty()) return

        val generator = PyElementGenerator.getInstance(project)
        val argumentList = call.argumentList ?: return
        val languageLevel = LanguageLevel.forElement(file)

        // Always use the recursive generator which handles both nested dataclasses and leaf alias wrapping.
        populateRecursively(
            project,
            file,
            call,
            argumentList,
            missing,
            context,
            generator,
            languageLevel,
            options,
            unionSelections
        )
    }

    fun missingParametersFor(
        call: PyCallExpression,
        options: PopulateOptions,
        context: TypeEvalContext
    ): List<PyCallableParameter> {
        return if (options.useLocalScope) {
            // In locals-mode we may also fill optional/defaulted params, but only when a local match exists.
            getMissingParameters(call, context)
        } else {
            when (options.mode) {
                PopulateMode.ALL -> getMissingParameters(call, context)
                PopulateMode.REQUIRED_ONLY -> getMissingRequiredParameters(call, context)
            }
        }
    }

    fun chooseUnionMembers(
        editor: Editor,
        popupHost: PopupHost,
        missing: List<PyCallableParameter>,
        context: TypeEvalContext,
        onComplete: (Map<String, String>) -> Unit
    ) {
        val requests = collectUnionChoiceRequests(missing, context)
        if (requests.isEmpty()) {
            onComplete(emptyMap())
            return
        }

        val selections = mutableMapOf<String, String>()

        fun chooseNext(index: Int) {
            if (index >= requests.size) {
                onComplete(selections)
                return
            }
            val request = requests[index]
            popupHost.showChooser(
                editor = editor,
                title = "Select union type for ${request.paramName}",
                items = request.options,
                render = { valueGenerator.renderUnionChoiceLabel(it) },
                onChosen = { chosen ->
                    selections[request.signature] = valueGenerator.renderTypeName(chosen)
                    chooseNext(index + 1)
                }
            )
        }

        chooseNext(0)
    }

    fun hasUnionChoices(missing: List<PyCallableParameter>, context: TypeEvalContext): Boolean {
        return collectUnionChoiceRequests(missing, context).isNotEmpty()
    }

    fun buildPreviewText(
        project: Project,
        file: PyFile,
        editor: Editor,
        options: PopulateOptions
    ): String? {
        val previewFile = file.copy() as? PyFile ?: return null
        val previewCall = callFinder.findCallExpression(editor, previewFile) ?: return null
        val previewContext = TypeEvalContext.codeAnalysis(project, previewFile)

        IntentionPreviewUtils.write<RuntimeException> {
            populateArguments(project, previewFile, previewCall, options, previewContext)
        }

        val updatedCall = callFinder.findCallExpression(editor, previewFile) ?: previewCall
        return updatedCall.text
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
        options: PopulateOptions,
        unionSelections: Map<String, String>
    ) {
        val calleeClass: PyClass? = (call.callee as? PyReferenceExpression)
            ?.reference
            ?.resolve()
            ?.let { it as? PyClass }

        fun sanitizeNoEllipsis(result: PyValueGenerator.GenerationResult): PyValueGenerator.GenerationResult {
            return if (result.text.contains("...")) {
                PyValueGenerator.GenerationResult("None", emptySet())
            } else {
                result
            }
        }

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
                } else if (param.hasDefaultValue()) {
                    // Optional/defaulted params should only be populated when we can map from locals.
                    // If we can't, leave them untouched.
                    return@mapNotNull null
                } else {
                    // Required param with no local match: fall back to generated value, but never use ellipsis.
                    sanitizeNoEllipsis(
                        valueGenerator.generateValue(
                            type,
                            context,
                            0,
                            generator,
                            languageLevel,
                            file,
                            unionSelections,
                            options.useConstructors
                        )
                    )
                }
            } else {
                valueGenerator.generateValue(
                    type,
                    context,
                    0,
                    generator,
                    languageLevel,
                    file,
                    unionSelections,
                    options.useConstructors
                )
            }

            // If we only have a leaf "..." or a generated alias, ensure we have the correct import
            // by looking at the dataclass field annotation (e.g. for NewType).
            var finalResult = result
            if (calleeClass != null) {
                val field = calleeClass.findClassAttribute(name, true, context)
                val aliasRef = field?.annotation?.value as? PyReferenceExpression
                val aliasName = aliasRef?.name

                if (!aliasName.isNullOrBlank() && !PyBuiltinNames.isBuiltin(aliasName)) {
                    val expectedText = "$aliasName(...)"
                    if (finalResult.text == "..." || finalResult.text == expectedText) {
                        val resolved = aliasRef.reference.resolve() as? PsiNamedElement
                        // If we resolved the alias, and it's missing from imports or we are upgrading from "...", use it.
                        if (resolved != null && (finalResult.text == "..." || !finalResult.imports.contains(resolved))) {
                            finalResult = PyValueGenerator.GenerationResult(expectedText, setOf(resolved))
                        }
                    }
                }
            }

            if (options.useLocalScope) {
                // Locals-mode must never insert ellipsis placeholders.
                finalResult = sanitizeNoEllipsis(finalResult)
            }

            Triple(param, name, finalResult)
        }

        // Apply arguments.
        // When the call currently has *no* arguments, it is more robust to replace the whole argument list
        // with a freshly parsed one, rather than relying on incremental PSI insertions (which can sometimes
        // lead to arguments being inserted into nested calls).
        if (argumentList.arguments.isEmpty()) {
            val argsText = paramData.joinToString(", ") { (_, name, result) -> "$name=${result.text}" }
            val calleeText = call.callee?.text ?: return
            val parsedCall =
                generator.createExpressionFromText(languageLevel, "$calleeText($argsText)") as? PyCallExpression
                    ?: return
            val newArgList = parsedCall.argumentList ?: return

            argumentList.replace(newArgList)
            val replacedArgList = call.argumentList ?: return

            // Ensure imports for all inserted values
            for ((_, name, result) in paramData) {
                val kwArg = replacedArgList.arguments.filterIsInstance<PyKeywordArgument>().find { it.keyword == name }
                val valueExpression = kwArg?.valueExpression
                if (valueExpression != null) {
                    for (cls in result.imports) {
                        imports.ensureImportedIfNeeded(file, valueExpression, cls)
                    }
                }
            }
        } else {
            // Otherwise, append missing keyword arguments and ensure imports.
            for ((_, name, result) in paramData) {
                val kwArg = generator.createKeywordArgument(languageLevel, name, result.text)
                argumentList.addArgument(kwArg)
                val added = argumentList.arguments.filterIsInstance<PyKeywordArgument>().find { it.keyword == name }
                val valueExpression = (added ?: kwArg).valueExpression
                if (valueExpression != null) {
                    for (cls in result.imports) {
                        imports.ensureImportedIfNeeded(file, valueExpression, cls)
                    }
                }
            }
        }

        if (call !is PyDecorator && call.parent !is PyDecorator) {
            // `argumentList` may have been replaced above; always re-fetch from the call to avoid
            // formatting a stale/detached PSI element.
            call.argumentList?.let { CodeStyleManager.getInstance(project).reformat(it) }
        }
    }

    private fun collectUnionChoiceRequests(
        missing: List<PyCallableParameter>,
        context: TypeEvalContext
    ): List<UnionChoiceRequest> {
        return missing.mapNotNull { param ->
            val name = param.name ?: return@mapNotNull null
            val union = param.getType(context) as? PyUnionType ?: return@mapNotNull null
            val members = valueGenerator.normalizeUnionMembers(union)
            val ranked = valueGenerator.rankUnionMembers(members)
            val nonNone = ranked.filterNot { valueGenerator.isNoneType(it) }
            if (nonNone.size <= 1) return@mapNotNull null
            UnionChoiceRequest(
                paramName = name,
                signature = valueGenerator.unionSignature(members),
                options = ranked
            )
        }
    }

    internal data class UnionChoiceRequest(
        val paramName: String,
        val signature: String,
        val options: List<PyType>
    )
}
