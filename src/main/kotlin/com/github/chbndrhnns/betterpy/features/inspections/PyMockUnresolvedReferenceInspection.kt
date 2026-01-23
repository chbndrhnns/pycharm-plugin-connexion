package com.github.chbndrhnns.betterpy.features.inspections

import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.type.PyMockType
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext

class PyMockUnresolvedReferenceInspection : PyInspection() {

    override fun getShortName(): String = "PyMockUnresolvedReference"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        return object : PyElementVisitor() {
            override fun visitPyReferenceExpression(node: PyReferenceExpression) {
                super.visitPyReferenceExpression(node)

                if (!PluginSettingsState.instance().state.enablePyMockUnresolvedReferenceInspection) return

                val qualifier = node.qualifier ?: return
                val context = TypeEvalContext.codeAnalysis(node.project, node.containingFile)
                val qualifierType = context.getType(qualifier)

                if (qualifierType is PyMockType) {
                    // If it's a Mock type, we check if the reference resolves.
                    // Since PyMockType handles resolution (delegating to spec + mock),
                    // if it fails to resolve, we should flag it.

                    // We use multiResolve to check if there are any results.
                    val resolveResults = node.reference.multiResolve(false)
                    if (resolveResults.isEmpty()) {
                        val name = node.name ?: return
                        // Highlight only the name part (identifier)
                        val highlightElement = node.lastChild ?: node
                        holder.registerProblem(
                            highlightElement,
                            "Unresolved attribute reference '$name' for class '${qualifierType.name}'"
                        )
                    }
                }
            }
        }
    }
}
