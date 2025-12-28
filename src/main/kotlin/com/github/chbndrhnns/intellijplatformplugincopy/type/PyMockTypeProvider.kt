package com.github.chbndrhnns.intellijplatformplugincopy.type

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

class PyMockTypeProvider : PyTypeProviderBase() {
    override fun getCallType(
        function: PyFunction,
        callSite: PyCallSiteExpression,
        context: TypeEvalContext
    ): Ref<PyType>? {
        if (!PluginSettingsState.instance().state.enablePyMockTypeProvider) {
            return null
        }

        if (callSite !is PyCallExpression) {
            return null
        }

        val callee = callSite.callee as? PyReferenceExpression ?: return null
        val resolved = callee.reference.resolve()

        var isMock = false
        var isAsync = false

        if (resolved is PyClass) {
            val qname = resolved.qualifiedName
            if (qname == "unittest.mock.Mock" || qname == "unittest.mock.MagicMock") {
                isMock = true
            } else if (qname == "unittest.mock.AsyncMock") {
                isMock = true
                isAsync = true
            }
        }

        if (isMock) {
            val specArg = callSite.getKeywordArgument("spec") ?: callSite.getKeywordArgument("spec_set")
            if (specArg != null) {
                val specType = context.getType(specArg)
                if (specType != null) {
                    return Ref.create(PyMockType(specType, isAsync = isAsync))
                }
            }
        }
        return null
    }

    override fun getReferenceType(
        referenceTarget: PsiElement,
        context: TypeEvalContext,
        anchor: PsiElement?
    ): Ref<PyType>? {
        if (!PluginSettingsState.instance().state.enablePyMockTypeProvider) {
            return null
        }

        // Case 1: Resolving the type of a variable (PyTargetExpression) that was assigned a Mock
        if (referenceTarget is PyTargetExpression) {
            val assignedValue = referenceTarget.findAssignedValue()
            if (assignedValue is PyCallExpression) {
                val callee = assignedValue.callee as? PyReferenceExpression
                val resolved = callee?.reference?.resolve()

                if (resolved is PyClass) {
                    val qname = resolved.qualifiedName
                    if (qname == "unittest.mock.Mock" || qname == "unittest.mock.MagicMock" || qname == "unittest.mock.AsyncMock") {
                        val specArg =
                            assignedValue.getKeywordArgument("spec") ?: assignedValue.getKeywordArgument("spec_set")
                        if (specArg != null) {
                            val specType = context.getType(specArg)
                            if (specType != null) {
                                return Ref.create(PyMockType(specType, isAsync = (qname == "unittest.mock.AsyncMock")))
                            }
                        }
                    }
                }
            }
        }

        // Case 2: Resolving member access on a Mock object
        if (anchor is PyReferenceExpression) {
            val qualifier = anchor.qualifier
            if (qualifier != null) {
                val qualifierType = context.getType(qualifier)
                if (qualifierType is PyMockType) {
                    // We found an access on a mock object.
                    // The referenceTarget is the element resolved from the spec (because PyMockType delegates resolution).
                    // We want to return a Mock wrapping the type of that element.

                    // Get the type of the target element
                    if (referenceTarget is PyTypedElement) {
                        var targetType = context.getType(referenceTarget)

                        // Handle properties: if target is a property, we want the type of the property value, not the function type.
                        if (referenceTarget is PyFunction) {
                            val decoratorList = referenceTarget.decoratorList
                            if (decoratorList != null && decoratorList.decorators.any { it.name == "property" || it.qualifiedName?.toString() == "builtins.property" }) {
                                val returnType = context.getReturnType(referenceTarget)
                                if (returnType != null) {
                                    targetType = returnType
                                }
                            }
                        }

                        if (targetType != null) {
                            // If it's an async mock and we are accessing a method, the method mock should probably also be async-aware?
                            // But usually AsyncMock methods are just AsyncMocks.
                            // For now propagate isAsync.
                            return Ref.create(PyMockType(targetType, isAsync = qualifierType.isAsync))
                        }
                    }
                }
            }
        }
        return null
    }
}
