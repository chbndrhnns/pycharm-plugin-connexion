package com.github.chbndrhnns.intellijplatformplugincopy.features.type

import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyMockType(
    val specType: PyType,
    val isAsync: Boolean
) : PyType, PyCallableType {

    override fun getName(): String {
        return "Mock(spec=${specType.name})"
    }

    override fun getCompletionVariants(
        completionPrefix: String?,
        location: PsiElement?,
        context: ProcessingContext?
    ): Array<Any> {
        val specVariants = specType.getCompletionVariants(completionPrefix, location, context).toMutableSet()

        if (location != null) {
            val project = location.project
            val mockClass = PyPsiFacade.getInstance(project).createClassByQName("unittest.mock.Mock", location)
            if (mockClass != null) {
                // We create a type for Mock class to get its completion variants
                val mockType =
                    TypeEvalContext.codeAnalysis(project, location.containingFile)
                        .getType(mockClass)
                if (mockType != null) {
                    val mockVariants = mockType.getCompletionVariants(completionPrefix, location, context)
                    specVariants.addAll(mockVariants)
                }
            }
        }
        return specVariants.toTypedArray()
    }

    override fun resolveMember(
        name: String,
        location: PyExpression?,
        direction: AccessDirection,
        resolveContext: PyResolveContext
    ): List<RatedResolveResult>? {
        // 1. Try spec
        val specMembers = specType.resolveMember(name, location, direction, resolveContext)
        if (!specMembers.isNullOrEmpty()) {
            return specMembers
        }

        // 2. Try unittest.mock.Mock
        if (location != null) {
            val project = location.project
            val mockClass = PyPsiFacade.getInstance(project).createClassByQName("unittest.mock.Mock", location)
            if (mockClass != null) {
                val context = resolveContext.typeEvalContext
                val mockType = context.getType(mockClass)
                if (mockType != null) {
                    val mockMembers = mockType.resolveMember(name, location, direction, resolveContext)
                    if (!mockMembers.isNullOrEmpty()) {
                        return mockMembers
                    }
                }
            }
        }

        return null
    }

    override fun isBuiltin(): Boolean = false

    override fun assertValid(value: String?) {
        specType.assertValid(value)
    }

    override fun isCallable(): Boolean {
        // If the spec type is callable, then the mock of it is callable.
        // Also if it's a Mock object wrapping a function type, it is callable.
        // If it's a Mock object wrapping a Class, it's callable only if Class has __call__.
        if (specType is PyCallableType && specType.isCallable) return true
        // For simple delegation:
        return (specType as? PyCallableType)?.isCallable == true
    }

    override fun getReturnType(context: TypeEvalContext): PyType? {
        return (specType as? PyCallableType)?.getReturnType(context)
    }

    override fun getCallType(context: TypeEvalContext, callSite: PyCallSiteExpression): PyType {
        // When calling a Mock object, it returns itself (the mock), not the spec's return type
        return this
    }

    override fun getParameters(context: TypeEvalContext): List<PyCallableParameter>? {
        return (specType as? PyCallableType)?.getParameters(context)
    }
}
