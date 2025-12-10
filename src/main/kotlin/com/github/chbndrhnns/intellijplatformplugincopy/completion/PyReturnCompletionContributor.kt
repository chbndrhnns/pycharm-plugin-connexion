package com.github.chbndrhnns.intellijplatformplugincopy.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReturnStatement
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyReturnCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            psiElement().withLanguage(PythonLanguage.INSTANCE)
                .inside(PyReturnStatement::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val position = parameters.position

                    // Ensure we are at the start of the return statement (e.g. "return <caret>" or "return i<caret>")
                    // We don't want to offer types if we are deep in an expression like "return 1 + <caret>"
                    val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position)
                    if (prevLeaf == null || prevLeaf.text != "return") {
                        return
                    }

                    val function = PsiTreeUtil.getParentOfType(position, PyFunction::class.java) ?: return

                    val typeEvalContext = TypeEvalContext.codeAnalysis(position.project, position.containingFile)
                    val returnType = typeEvalContext.getReturnType(function) ?: return

                    val types = if (returnType is PyUnionType) {
                        returnType.members
                    } else {
                        listOf(returnType)
                    }

                    types.forEach { type ->
                        val typeName = type?.name
                        if (typeName != null) {
                            val element = LookupElementBuilder.create(typeName)
                                .withTypeText("Expected return type")
                            val prioritized = PrioritizedLookupElement.withPriority(element, 100.0)
                            result.addElement(prioritized)
                        }
                    }
                }
            }
        )
    }
}
