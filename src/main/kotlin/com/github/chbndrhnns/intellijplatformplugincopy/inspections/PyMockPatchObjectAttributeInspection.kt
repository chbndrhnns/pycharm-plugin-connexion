package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Inspection that checks if the attribute string in `unittest.mock.patch.object(target, "attribute")`
 * actually exists on the target class.
 */
class PyMockPatchObjectAttributeInspection : PyInspection() {

    override fun getShortName(): String = "PyMockPatchObjectAttribute"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return object : PyElementVisitor() {
            override fun visitPyCallExpression(node: PyCallExpression) {
                super.visitPyCallExpression(node)

                if (!PluginSettingsState.instance().state.enablePyMockPatchReferenceContributor) return

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
                return true
            }
        }
        return false
    }
}
