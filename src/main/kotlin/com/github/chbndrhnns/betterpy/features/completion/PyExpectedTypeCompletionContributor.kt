package com.github.chbndrhnns.betterpy.features.completion

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.intentions.populate.PyDataclassFieldExtractor
import com.github.chbndrhnns.betterpy.features.intentions.populate.PyValueGenerator
import com.github.chbndrhnns.betterpy.features.intentions.shared.ExpectedTypeInfo
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.*

class PyExpectedTypeCompletionContributor : CompletionContributor() {

    private val valueGenerator = PyValueGenerator(PyDataclassFieldExtractor())

    init {
        extend(
            CompletionType.BASIC,
            psiElement().withLanguage(PythonLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    if (!PluginSettingsState.instance().state.enableExpectedTypeCompletionContributor) {
                        return
                    }
                    val position = parameters.position
                    val expression = PsiTreeUtil.getParentOfType(position, PyExpression::class.java) ?: return

                    if (expression is PyReferenceExpression && expression.isQualified) {
                        // Do not offer on attribute access
                        return
                    }

                    val typeEvalContext = TypeEvalContext.codeAnalysis(position.project, position.containingFile)

                    // Use ExpectedTypeInfo to get the expected type at this position
                    val typeInfo = ExpectedTypeInfo.getExpectedTypeInfo(expression, typeEvalContext) ?: return
                    val expectedType = typeInfo.type ?: return

                    val types = if (expectedType is PyUnionType) {
                        expectedType.members
                    } else {
                        listOf(expectedType)
                    }

                    types
                        .asSequence()
                        .filterNotNull()
                        .filterNot { shouldSkipExpectedTypeSuggestion(it, expression) }
                        .forEach { type ->
                            val generator = PyElementGenerator.getInstance(position.project)
                            val languageLevel = LanguageLevel.forElement(position)
                            val scopeOwner = ScopeUtil.getScopeOwner(position)

                            val generatedValue = if (scopeOwner != null) {
                                generateFromAnnotation(
                                    typeInfo.annotationExpr,
                                    type,
                                    typeEvalContext,
                                    generator,
                                    languageLevel,
                                    scopeOwner
                                ) ?: valueGenerator.generateValue(
                                    type,
                                    typeEvalContext,
                                    0,
                                    generator,
                                    languageLevel,
                                    scopeOwner
                                )
                            } else null

                            val generatedText = generatedValue?.text ?: "..."

                            val lookupString =
                                if (generatedText == "..." || generatedText == "None") type.name else generatedText

                            if (lookupString != null) {
                                val element = LookupElementBuilder.create(lookupString)
                                    .withTypeText("Expected type")
                                val prioritized = PrioritizedLookupElement.withPriority(element, 10000.0)
                                result.addElement(prioritized)
                            }
                        }
                }
            }
        )
    }

    private fun shouldSkipExpectedTypeSuggestion(type: PyType, anchor: PyExpression): Boolean {
        if (type is PyClassType) {
            val pyClass = type.pyClass
            if (PyBuiltinCache.getInstance(anchor).isBuiltin(pyClass)) {
                // Skip simple builtins like str, int, etc.
                // But allow parameterized collections like list[MyType] because they generate useful literals.
                if (type !is PyCollectionType || type.elementTypes.filterNotNull().isEmpty()) {
                    return true
                }
            }
        }

        val name = type.name ?: return true

        // `typing.LiteralString` (Py3.11+) and some internal literal-string types can appear as these.
        if (name.contains("LiteralString", ignoreCase = true)) return true
        if (name.contains("LiteralStr", ignoreCase = true)) return true

        if (name.equals("Literal", ignoreCase = true) || name.equals("typing.Literal", ignoreCase = true)) return true

        // Generic literal types like `Literal["x"]` shouldn’t be offered as expected-type suggestions.
        if (name.startsWith("Literal[")) return true

        return false
    }

    private fun generateFromAnnotation(
        annotationExpr: PyTypedElement?,
        type: PyType,
        context: TypeEvalContext,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        scopeOwner: ScopeOwner
    ): PyValueGenerator.GenerationResult? {
        val subscription = annotationExpr as? PySubscriptionExpression ?: return null
        if (type !is PyClassType && type !is PyCollectionType) return null

        val baseName = (subscription.operand as? PyQualifiedExpression)?.name
            ?: (subscription.operand as? PyReferenceExpression)?.name
            ?: return null

        val normalized = baseName.lowercase()
        if (normalized != "list" && normalized != "set" && normalized != "tuple") return null

        val indexExpr = subscription.indexExpression ?: return null
        val elementExpr = if (normalized == "tuple" && indexExpr is PyTupleExpression) {
            indexExpr.elements.firstOrNull()
        } else indexExpr

        val elementType = elementExpr?.let { context.getType(it) } ?: return null
        val elementResult = valueGenerator.generateValue(
            elementType,
            context,
            0,
            generator,
            languageLevel,
            scopeOwner
        )

        val elementText = if (elementResult.text == "...") {
            val ctorName = ExpectedTypeInfo.canonicalCtorName(elementExpr, context)
            ctorName?.let { "$it()" } ?: return null
        } else {
            elementResult.text
        }

        val text = when (normalized) {
            "list" -> "[$elementText]"
            "set" -> "{$elementText}"
            "tuple" -> "($elementText)"
            else -> return null
        }
        return PyValueGenerator.GenerationResult(text, elementResult.imports)
    }
}
