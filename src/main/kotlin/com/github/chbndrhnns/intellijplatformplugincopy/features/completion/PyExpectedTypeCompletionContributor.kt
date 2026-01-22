package com.github.chbndrhnns.intellijplatformplugincopy.features.completion

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.populate.PyDataclassFieldExtractor
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.populate.PyValueGenerator
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.shared.ExpectedTypeInfo
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyReferenceExpression
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
                                valueGenerator.generateValue(
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
}
