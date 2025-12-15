package com.github.chbndrhnns.intellijplatformplugincopy.search

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
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
            // Use projectScope to exclude third-party libraries and stdlib, limiting to source/test roots
            val scope = queryParameters.scope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(pyClass.project)

            // Find class implementations
            val implementations = PyProtocolImplementationsSearch.search(pyClass, scope, context)
            
            for (impl in implementations) {
                if (!consumer.process(impl)) return@compute false
            }

            // For callable-only protocols, also find matching functions and lambdas
            if (PyProtocolImplementationsSearch.isCallableOnlyProtocol(pyClass, context)) {
                val matchingFunctions = findFunctionsMatchingCallableProtocol(pyClass, scope, context)
                for (func in matchingFunctions) {
                    if (!consumer.process(func)) return@compute false
                }

                val matchingLambdas = findLambdasMatchingCallableProtocol(pyClass, scope, context)
                for (lambda in matchingLambdas) {
                    if (!consumer.process(lambda)) return@compute false
                }
            }
            
            true
        }
    }

    /**
     * Finds all top-level functions that match a callable-only protocol's __call__ signature.
     */
    private fun findFunctionsMatchingCallableProtocol(
        protocol: PyClass,
        scope: GlobalSearchScope,
        context: TypeEvalContext
    ): Collection<PyFunction> {
        val matchingFunctions = mutableListOf<PyFunction>()
        val project = protocol.project
        val allFunctionNames = mutableListOf<String>()

        StubIndex.getInstance().processAllKeys(
            PyFunctionNameIndex.KEY, project
        ) { functionName ->
            allFunctionNames.add(functionName)
            true
        }

        for (functionName in allFunctionNames) {
            ProgressManager.checkCanceled()
            val functions = PyFunctionNameIndex.find(functionName, project, scope)
            for (func in functions) {
                // Only consider top-level functions (not methods)
                if (func.containingClass != null) continue
                // Exclude test functions
                if (PyProtocolImplementationsSearch.isTestFunction(func)) continue

                val funcType = context.getType(func) as? PyCallableType ?: continue

                if (PyProtocolImplementationsSearch.isCallableCompatibleWithCallProtocol(funcType, protocol, context)) {
                    matchingFunctions.add(func)
                }
            }
        }

        return matchingFunctions
    }

    /**
     * Finds all lambda expressions that are used in a context where the protocol type is expected
     * and match the protocol's __call__ signature.
     * 
     * Only includes lambdas that are:
     * - Passed as arguments where the parameter type is the protocol
     * - Assigned to variables annotated with the protocol type
     * - Used in other contexts where the expected type is the protocol
     */
    private fun findLambdasMatchingCallableProtocol(
        protocol: PyClass,
        scope: GlobalSearchScope,
        context: TypeEvalContext
    ): Collection<PyLambdaExpression> {
        val matchingLambdas = mutableListOf<PyLambdaExpression>()
        val project = protocol.project
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        val protocolQName = protocol.qualifiedName

        // Find all Python files in scope
        val pythonFiles = FileTypeIndex.getFiles(PythonFileType.INSTANCE, scope)

        for (virtualFile in pythonFiles) {
            ProgressManager.checkCanceled()
            val psiFile = psiManager.findFile(virtualFile) as? PyFile ?: continue

            // Find all lambda expressions in the file
            val lambdas = PsiTreeUtil.findChildrenOfType(psiFile, PyLambdaExpression::class.java)

            for (lambda in lambdas) {
                // Check if the lambda is used in a context where the protocol type is expected
                val expectedType = getExpectedTypeForLambda(lambda, context)
                if (expectedType == null) continue

                // Check if the expected type matches the protocol
                val expectedClass = (expectedType as? PyClassType)?.pyClass
                if (expectedClass != protocol && expectedClass?.qualifiedName != protocolQName) continue

                val lambdaType = context.getType(lambda) as? PyCallableType ?: continue

                if (PyProtocolImplementationsSearch.isCallableCompatibleWithCallProtocol(
                        lambdaType,
                        protocol,
                        context
                    )
                ) {
                    matchingLambdas.add(lambda)
                }
            }
        }

        return matchingLambdas
    }

    /**
     * Gets the expected type for a lambda expression based on its usage context.
     * Returns null if no expected type can be determined.
     */
    private fun getExpectedTypeForLambda(lambda: PyLambdaExpression, context: TypeEvalContext): PyType? {
        val parent = lambda.parent

        // Case 1: Lambda is an argument in a function call
        if (parent is PyArgumentList) {
            val call = parent.parent as? PyCallExpression ?: return null
            val callee = call.callee ?: return null
            val calleeType = context.getType(callee)

            if (calleeType is PyCallableType) {
                val params = calleeType.getParameters(context) ?: return null
                val argIndex = parent.arguments.indexOf(lambda)
                if (argIndex >= 0 && argIndex < params.size) {
                    return params[argIndex].getType(context)
                }
            }
        }

        // Case 2: Lambda is assigned to a variable with type annotation
        if (parent is PyAssignmentStatement) {
            val targets = parent.targets
            if (targets.isNotEmpty()) {
                val target = targets[0]
                if (target is PyTargetExpression) {
                    val annotation = target.annotation?.value
                    if (annotation != null) {
                        return context.getType(annotation)
                    }
                }
            }
        }

        // Case 3: Lambda is the value in a named expression (walrus operator) or other contexts
        // For now, we only support the most common cases above

        return null
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

            // Use projectScope to exclude third-party libraries and stdlib, limiting to source/test roots
            val scope = queryParameters.scope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(method.project)
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

            // Use projectScope to exclude third-party libraries and stdlib, limiting to source/test roots
            val scope = queryParameters.scope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(attribute.project)
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
