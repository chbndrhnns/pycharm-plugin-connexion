package com.github.chbndrhnns.betterpy.features.type

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassLikeType
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
                    // First check the current function scope
                    val containingScope = PsiTreeUtil.getParentOfType(anchor, PyFunction::class.java)
                    if (containingScope != null && isPatchedAttribute(containingScope, qualifierTarget, attrName)) {
                        return Ref.create(PyMockType(null, isAsync = false))
                    }
                    // Then check the entire file for patch.object calls targeting the same class + attribute
                    val containingClass = (referenceTarget as? PyFunction)?.containingClass
                    if (containingClass != null) {
                        val file = anchor.containingFile
                        if (file != null && isPatchedAttributeByClass(file, containingClass, attrName, context)) {
                            return Ref.create(PyMockType(null, isAsync = false))
                        }
                    }
                }
            }
        }

        // Case 2b: Parameter injected by @patch.object decorator is a mock
        if (referenceTarget is PyNamedParameter) {
            val func = PsiTreeUtil.getParentOfType(referenceTarget, PyFunction::class.java)
            if (func != null) {
                val mockType = getMockTypeFromPatchDecorator(func, referenceTarget)
                if (mockType != null) {
                    return Ref.create(mockType)
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

    /**
     * Checks if the given attribute is patched via patch.object() anywhere in the file,
     * matching by the containing class of the attribute and the attribute name.
     * This handles cross-scope cases like fixtures and helper functions.
     */
    private fun isPatchedAttributeByClass(
        file: PsiElement,
        containingClass: PyClass,
        attrName: String,
        context: TypeEvalContext
    ): Boolean {
        val classQName = containingClass.qualifiedName ?: return false
        val calls = PsiTreeUtil.findChildrenOfType(file, PyCallExpression::class.java)
        for (call in calls) {
            val callee = call.callee as? PyReferenceExpression ?: continue
            if (callee.name != "object") continue

            val patchRef = callee.qualifier as? PyReferenceExpression ?: continue
            if (patchRef.name != "patch") continue

            val args = call.argumentList?.arguments ?: continue
            if (args.size < 2) continue

            // Check that the second argument is a string matching the attribute name
            val attrArg = args[1] as? PyStringLiteralExpression ?: continue
            if (attrArg.stringValue != attrName) continue

            // Check that the first argument's type matches the containing class
            val targetArg = args[0]
            val targetType = context.getType(targetArg)
            if (targetType is PyClassLikeType && targetType.classQName == classQName) {
                return true
            }
            // Also check if the first argument resolves to the class itself (e.g., patch.object(Service, "fetch"))
            if (targetArg is PyReferenceExpression) {
                val resolved = targetArg.reference.resolve()
                if (resolved is PyClass && resolved.qualifiedName == classQName) {
                    return true
                }
            }
            // If the first argument is untyped (e.g., a parameter without annotation),
            // accept the match based on attribute name alone — this handles cross-scope helpers/fixtures
            if (targetType == null) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a parameter is injected by a @patch.object or @patch decorator.
     * Decorators are applied bottom-up, so the last decorator's mock is the first extra parameter.
     */
    private fun getMockTypeFromPatchDecorator(func: PyFunction, param: PyNamedParameter): PyMockType? {
        val decoratorList = func.decoratorList ?: return null
        val decorators = decoratorList.decorators

        // Find patch/patch.object decorators
        val patchDecorators = decorators.filter { decorator ->
            val expr = decorator.expression as? PyCallExpression ?: return@filter false
            val callee = expr.callee as? PyReferenceExpression ?: return@filter false
            // patch.object(...) or patch(...)
            (callee.name == "object" && (callee.qualifier as? PyReferenceExpression)?.name == "patch") ||
                    (callee.name == "patch" && callee.qualifier == null)
        }

        if (patchDecorators.isEmpty()) return null

        // Parameters: first are the function's own params (self, regular params, fixtures),
        // then patch decorators inject params in reverse order (bottom decorator = first injected param)
        val paramList = func.parameterList.parameters.filterIsInstance<PyNamedParameter>()
        // The injected mock params are the last N params where N = number of patch decorators
        val regularParamCount = paramList.size - patchDecorators.size
        if (regularParamCount < 0) return null

        val paramIndex = paramList.indexOf(param)
        if (paramIndex < regularParamCount) return null

        // This param corresponds to a patch decorator
        // Decorators are applied bottom-up, so reverse the list
        val decoratorIndex = paramIndex - regularParamCount
        val reversedPatchDecorators = patchDecorators.reversed()
        if (decoratorIndex >= reversedPatchDecorators.size) return null

        return PyMockType(null, isAsync = false)
    }
}
