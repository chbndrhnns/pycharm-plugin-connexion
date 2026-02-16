package com.github.chbndrhnns.betterpy.features.type

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
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
            // Plain mock without spec - still return PyMockType so mock members resolve
            return Ref.create(PyMockType(null, isAsync = isAsync))
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
                        val isAsync = qname == "unittest.mock.AsyncMock"
                        val specArg =
                            assignedValue.getKeywordArgument("spec") ?: assignedValue.getKeywordArgument("spec_set")
                        if (specArg != null) {
                            val specType = context.getType(specArg)
                            if (specType != null) {
                                return Ref.create(PyMockType(specType, isAsync = isAsync))
                            }
                        }
                        // Plain mock without spec
                        return Ref.create(PyMockType(null, isAsync = isAsync))
                    }
                }
            }
        }

        // Case 2: Attribute access on an object whose attribute was patched via patch.object
        if (anchor is PyReferenceExpression && referenceTarget is PyFunction) {
            val qualifier = anchor.qualifier as? PyReferenceExpression
            val attrName = anchor.name
            if (qualifier != null && attrName != null) {
                val qualifierTarget = qualifier.reference.resolve()
                if (qualifierTarget != null) {
                    val containingScope = PsiTreeUtil.getParentOfType(anchor, PyFunction::class.java)
                    if (containingScope != null && isPatchedAttribute(containingScope, qualifierTarget, attrName)) {
                        return Ref.create(PyMockType(null, isAsync = false))
                    }
                }
            }
        }

        // Case 3: Resolving member access on a Mock object
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

    /**
     * Checks if the given attribute on the given target was patched via patch.object() in the scope.
     * Looks for calls like: mocker.patch.object(target, "attrName") or patch.object(target, "attrName")
     */
    private fun isPatchedAttribute(scope: PyFunction, qualifierTarget: PsiElement, attrName: String): Boolean {
        val calls = PsiTreeUtil.findChildrenOfType(scope, PyCallExpression::class.java)
        for (call in calls) {
            val callee = call.callee as? PyReferenceExpression ?: continue
            if (callee.name != "object") continue

            val patchRef = callee.qualifier as? PyReferenceExpression ?: continue
            if (patchRef.name != "patch") continue

            val args = call.argumentList?.arguments ?: continue
            if (args.size < 2) continue

            // Check that the first argument resolves to the same target
            val targetArg = args[0] as? PyReferenceExpression ?: continue
            val targetResolved = targetArg.reference.resolve() ?: continue
            if (targetResolved != qualifierTarget) continue

            // Check that the second argument is a string matching the attribute name
            val attrArg = args[1] as? PyStringLiteralExpression ?: continue
            if (attrArg.stringValue == attrName) {
                return true
            }
        }
        return false
    }
}
