package com.github.chbndrhnns.intellijplatformplugincopy.search

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.types.TypeEvalContext

class PyProtocolDefinitionsSearchExecutor : QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
    override fun execute(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<in PsiElement>): Boolean {
        val element = queryParameters.element
        if (element !is PyClass) return true

        return ReadAction.compute<Boolean, RuntimeException> {
            val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
            val scope = queryParameters.scope as? GlobalSearchScope ?: GlobalSearchScope.allScope(element.project)
            
            val implementations = PyProtocolImplementationsSearch.search(element, scope, context)
            
            for (impl in implementations) {
                if (!consumer.process(impl)) return@compute false
            }
            true
        }
    }
}
