package com.github.chbndrhnns.betterpy.features.search

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.TypeEvalContext

class PyProtocolDefinitionsSearchExecutor : QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
    override fun execute(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<in PsiElement>): Boolean {
        return when (val element = queryParameters.element) {
            is PyClass -> processProtocolClass(element, queryParameters, consumer)
            is PyFunction -> processProtocolMethod(element, queryParameters, consumer)
            is PyTargetExpression -> processProtocolAttribute(element, queryParameters, consumer)
            else -> true
        }
    }

    /**
     * Gets the search scope from query parameters, defaulting to project scope.
     */
    private fun getSearchScope(
        queryParameters: DefinitionsScopedSearch.SearchParameters, project: Project
    ): GlobalSearchScope {
        return queryParameters.scope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(project)
    }
    
    /**
     * Process "Go to Implementation" for a Protocol class.
     * Returns all classes that structurally implement the Protocol.
     * For callable-only protocols (those with only __call__), also returns matching functions.
     */
    private fun processProtocolClass(
        pyClass: PyClass,
        queryParameters: DefinitionsScopedSearch.SearchParameters,
        consumer: Processor<in PsiElement>
    ): Boolean {
        return ReadAction.compute<Boolean, RuntimeException> {
            val context = TypeEvalContext.codeAnalysis(pyClass.project, pyClass.containingFile)
            val scope = getSearchScope(queryParameters, pyClass.project)

            // Find class implementations
            val implementations = PyProtocolImplementationsSearch.search(pyClass, scope, context)
            
            for (impl in implementations) {
                if (!consumer.process(impl)) return@compute false
            }

            // For callable-only protocols, also find matching functions and lambdas
            if (PyCallableProtocolMatcher.isCallableOnlyProtocol(pyClass, context)) {
                val matchingFunctions = PyCallableProtocolMatcher.findMatchingFunctions(pyClass, scope, context)
                for (func in matchingFunctions) {
                    if (!consumer.process(func)) return@compute false
                }

                val matchingLambdas = PyCallableProtocolMatcher.findMatchingLambdas(pyClass, scope, context)
                for (lambda in matchingLambdas) {
                    if (!consumer.process(lambda)) return@compute false
                }
            }
            
            true
        }
    }

    /**
     * Process "Go to Implementation" for a method defined in a Protocol.
     * Returns the corresponding methods in all implementing classes.
     */
    private fun processProtocolMethod(
        method: PyFunction,
        queryParameters: DefinitionsScopedSearch.SearchParameters,
        consumer: Processor<in PsiElement>
    ): Boolean {
        return ReadAction.compute<Boolean, RuntimeException> {
            val containingClass = method.containingClass ?: return@compute true
            val methodName = method.name ?: return@compute true
            
            val context = TypeEvalContext.codeAnalysis(method.project, method.containingFile)
            
            // Check if the containing class is a Protocol
            if (!PyProtocolImplementationsSearch.isProtocol(containingClass, context)) {
                return@compute true
            }

            val scope = getSearchScope(queryParameters, method.project)
            val implementations = PyProtocolImplementationsSearch.search(containingClass, scope, context)
            
            // Find the corresponding method in each implementing class
            for (impl in implementations) {
                val implMethod = impl.findMethodByName(methodName, false, context)
                if (implMethod != null) {
                    if (!consumer.process(implMethod)) return@compute false
                }
            }
            true
        }
    }
    
    /**
     * Process "Go to Implementation" for an attribute/property defined in a Protocol.
     * Returns the corresponding attributes in all implementing classes.
     */
    private fun processProtocolAttribute(
        attribute: PyTargetExpression,
        queryParameters: DefinitionsScopedSearch.SearchParameters,
        consumer: Processor<in PsiElement>
    ): Boolean {
        return ReadAction.compute<Boolean, RuntimeException> {
            val containingClass = PsiTreeUtil.getParentOfType(attribute, PyClass::class.java) 
                ?: return@compute true
            val attrName = attribute.name ?: return@compute true
            
            val context = TypeEvalContext.codeAnalysis(attribute.project, attribute.containingFile)
            
            // Check if the containing class is a Protocol
            if (!PyProtocolImplementationsSearch.isProtocol(containingClass, context)) {
                return@compute true
            }

            val scope = getSearchScope(queryParameters, attribute.project)
            val implementations = PyProtocolImplementationsSearch.search(containingClass, scope, context)
            
            // Find the corresponding attribute in each implementing class
            for (impl in implementations) {
                // Check class attributes
                val classAttr = impl.findClassAttribute(attrName, false, context)
                if (classAttr != null) {
                    if (!consumer.process(classAttr)) return@compute false
                    continue
                }
                
                // Check instance attributes
                val instanceAttr = impl.findInstanceAttribute(attrName, false)
                if (instanceAttr != null) {
                    if (!consumer.process(instanceAttr)) return@compute false
                }
            }
            true
        }
    }
}
