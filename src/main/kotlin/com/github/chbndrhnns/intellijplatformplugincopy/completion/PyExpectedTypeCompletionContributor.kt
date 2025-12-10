package com.github.chbndrhnns.intellijplatformplugincopy.completion

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedTypeInfo
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyExpectedTypeCompletionContributor : CompletionContributor() {
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

                    val typeEvalContext = TypeEvalContext.codeAnalysis(position.project, position.containingFile)

                    // Use ExpectedTypeInfo to get the expected type at this position
                    val typeInfo = ExpectedTypeInfo.getExpectedTypeInfo(expression, typeEvalContext) ?: return
                    val expectedType = typeInfo.type ?: return

                    val types = if (expectedType is PyUnionType) {
                        expectedType.members
                    } else {
                        listOf(expectedType)
                    }

                    types.forEach { type ->
                        val typeName = type?.name
                        if (typeName != null) {
                            val element = LookupElementBuilder.create(typeName)
                                .withTypeText("Expected type")
                            val prioritized = PrioritizedLookupElement.withPriority(element, 100.0)
                            result.addElement(prioritized)
                        }
                    }
                }
            }
        )
    }
}
