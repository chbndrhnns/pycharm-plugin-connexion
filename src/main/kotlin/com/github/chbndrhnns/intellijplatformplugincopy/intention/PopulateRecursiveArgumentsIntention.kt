package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.isDataclassClass
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.isPositionalOnlyCallable
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.PyImportService
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.resolveDataclassFieldParameters
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*
import javax.swing.Icon

/**
 * Intention that populates missing call arguments (including keyword-only parameters)
 * at the caret recursively for submodels (dataclasses, Pydantic models).
 *
 *     A(<caret>) -> A(b=B(c=...))
 */
class PopulateRecursiveArgumentsIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {

    private var text: String = "Populate missing arguments recursively"
    private val imports: PyImportService = PyImportService()

    override fun getText(): String = text

    override fun getFamilyName(): String = "Populate arguments"

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enablePopulateRecursiveArgumentsIntention) {
            return false
        }

        if (file !is PyFile) return false

        val call = findCallExpression(editor, file) ?: return false

        val ctx = TypeEvalContext.codeAnalysis(project, file)

        // Do not offer for positional-only function calls
        if (isPositionalOnlyCallable(call)) return false

        val missing = getMissingParameters(call, ctx)
        return missing.isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val pyFile = file as? PyFile ?: return
        val call = findCallExpression(editor, pyFile) ?: return

        val ctx = TypeEvalContext.userInitiated(project, pyFile)
        val missing = getMissingParameters(call, ctx)
        if (missing.isEmpty()) return

        val generator = PyElementGenerator.getInstance(project)
        val argumentList = call.argumentList ?: return
        val languageLevel = LanguageLevel.forElement(pyFile)

        val paramData = missing.mapNotNull { param ->
            val name = param.name ?: return@mapNotNull null
            val type = param.getType(ctx)
            val result = generateValue(type, ctx, 0, generator, languageLevel)
            Triple(param, name, result)
        }

        // First, create placeholder keyword arguments for all missing params to ensure
        // we always insert arguments in a stable way, even if valueExpr creation fails.
        for ((_, name, _) in paramData) {
            val kwArg = generator.createKeywordArgument(languageLevel, name, "None")
            argumentList.addArgument(kwArg)
        }

        // Then, replace placeholder values and ensure any referenced dataclass types
        // are imported using the shared PyImportService logic, reusing existing
        // relative imports like "from .models import Main" where possible.
        for ((_, name, result) in paramData) {
            val kwArg = argumentList.arguments.filterIsInstance<PyKeywordArgument>().find { it.keyword == name }
            if (kwArg != null) {
                val valueExpr = generator.createExpressionFromText(languageLevel, result.text)
                val replaced = kwArg.valueExpression?.replace(valueExpr) as? PyExpression ?: continue

                // Ensure imports for all classes used in generation
                for (cls in result.imports) {
                    imports.ensureImportedIfNeeded(pyFile, replaced, cls)
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

        return when {
            type is PyUnionType -> generateUnionValue(type, context, depth, generator, languageLevel)
            type is PyClassType && isDataclassClass(type.pyClass) -> generateDataclassValue(
                type.pyClass,
                context,
                depth,
                generator,
                languageLevel
            )

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

    override fun startInWriteAction(): Boolean = true

    private fun findCallExpression(editor: Editor, file: PyFile): PyCallExpression? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java, /* strict = */ false)
    }

    private fun getMissingParameters(call: PyCallExpression, context: TypeEvalContext): List<PyCallableParameter> {
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
            .toList()
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
