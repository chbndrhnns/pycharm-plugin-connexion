package com.github.chbndrhnns.intellijplatformplugincopy.completion

import com.github.chbndrhnns.intellijplatformplugincopy.intention.populate.PyDataclassFieldExtractor
import com.github.chbndrhnns.intellijplatformplugincopy.intention.populate.PyValueGenerator
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedTypeInfo
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
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
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

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
                    if (!PluginSettingsState.instance().state.enablePyReturnCompletionContributor) {
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
                        .filterNot { shouldSkipExpectedTypeSuggestion(it) }
                        .forEach { type ->
                            val generator = PyElementGenerator.getInstance(position.project)
                            val languageLevel = LanguageLevel.forElement(position)
                            val scopeOwner = ScopeUtil.getScopeOwner(position)

                            val generatedText = if (scopeOwner != null) {
                                valueGenerator.generateValue(
                                    type,
                                    typeEvalContext,
                                    0,
                                    generator,
                                    languageLevel,
                                    scopeOwner
                                ).text
                            } else "..."

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

    private fun shouldSkipExpectedTypeSuggestion(type: PyType): Boolean {
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
