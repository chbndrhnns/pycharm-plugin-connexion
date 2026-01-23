package com.github.chbndrhnns.betterpy.features.inspections

/**
 * Inspection that checks if the attribute string in `unittest.mock.patch.object(target, "attribute")`
 * actually exists on the target class.
 */
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

class PyMockPatchObjectAttributeInspection : PyInspection() {

    override fun getShortName(): String = "PyMockPatchObjectAttribute"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        return object : PyElementVisitor() {
            override fun visitPyCallExpression(node: PyCallExpression) {
                super.visitPyCallExpression(node)

                if (!PluginSettingsState.instance().state.enablePyMockPatchObjectAttributeInspection) return

                if (!isPatchObjectCall(node)) return

                val arguments = node.argumentList?.arguments ?: return
                if (arguments.size < 2) return

                val targetArg = arguments[0]
                val attributeArg = arguments[1] as? PyStringLiteralExpression ?: return
                val attributeName = attributeArg.stringValue

                val context = TypeEvalContext.codeAnalysis(node.project, node.containingFile)
                val targetType = context.getType(targetArg) ?: return

                // Check if the attribute exists on the target type
                val members = targetType.resolveMember(
                    attributeName,
                    null,
                    com.jetbrains.python.psi.AccessDirection.READ,
                    com.jetbrains.python.psi.resolve.PyResolveContext.defaultContext(context)
                )

                if (members.isNullOrEmpty()) {
                    holder.registerProblem(
                        attributeArg,
                        "Unresolved attribute reference '$attributeName' for type '${targetType.name ?: "Unknown"}'"
                    )
                }
            }
        }
    }

    private fun isPatchObjectCall(call: PyCallExpression): Boolean {
        val callee = call.callee as? PyReferenceExpression ?: return false

        // Check for patch.object pattern
        if (callee.name == "object") {
            val qualifier = callee.qualifier as? PyReferenceExpression
            if (qualifier?.name == "patch") {
                return isFromUnittestMock(qualifier)
            }
        }
        return false
    }

    private fun isFromUnittestMock(reference: PyReferenceExpression): Boolean {
        val resolveResults = reference.getReference().multiResolve(false)
        if (resolveResults.isEmpty()) {
            return reference.referencedName == "patch"
        }
        for (result in resolveResults) {
            val element = result.element ?: continue
            if (element is PyFunction && element.name == "patch") {
                val qualifiedName = element.qualifiedName
                if (qualifiedName == "unittest.mock.patch" || qualifiedName == "mock.patch" || qualifiedName == "pytest_mock.plugin.MockerFixture.patch") {
                    return true
                }
                // Fallback for tests or cases where FQN is not fully available
                val containingFile = element.containingFile
                if (containingFile is PyFile) {
                    val fileName = containingFile.name
                    if (fileName == "mock.py" || fileName == "pytest_mock.py") {
                        return true
                    }
                }
            }
            if (element is PyTargetExpression && element.name == "patch") {
                val qualifiedName = element.qualifiedName
                if (qualifiedName == "unittest.mock.patch" || qualifiedName == "mock.patch") {
                    return true
                }
                // If it's a target expression, it might be an alias like 'from unittest import mock as patch'
                // or just 'import unittest.mock as patch'
                val containingFile = element.containingFile
                if (containingFile is PyFile) {
                    val fileName = containingFile.name
                    if (fileName == "mock.py") {
                        return true
                    }
                }
            }
            if (element is PyClass && element.name == "patch") {
                 val qualifiedName = element.qualifiedName
                 if (qualifiedName == "unittest.mock.patch" || qualifiedName == "mock.patch") {
                     return true
                 }
            }
        }
        return false
    }
}
