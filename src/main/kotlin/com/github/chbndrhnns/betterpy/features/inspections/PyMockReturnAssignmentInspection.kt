package com.github.chbndrhnns.betterpy.features.inspections

import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.type.PyMockType
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

class PyMockReturnAssignmentInspection : PyInspection() {

    override fun getShortName(): String = "PyMockReturnAssignment"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        return object : PyElementVisitor() {
            override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
                super.visitPyAssignmentStatement(node)

                if (!PluginSettingsState.instance().state.enablePyMockReturnAssignmentInspection) return

                TypeEvalContext.codeAnalysis(node.project, node.containingFile)

                for (pair in node.targetsToValuesMapping) {
                    val target = pair.first
                    val value = pair.second

                    if (target is PyQualifiedExpression && value != null) {
                        val name = target.name
                        if (name == "return_value") {
                            val qualifier = target.qualifier
                            if (qualifier != null) {
                                val context = TypeEvalContext.codeAnalysis(node.project, node.containingFile)
                                val qualifierType = context.getType(qualifier)

                                val expectedReturnType = when {
                                    qualifierType is PyMockType && qualifierType.isCallable -> qualifierType.getReturnType(
                                        context
                                    )

                                    else -> {
                                        val baseQualifier = (qualifier as PyQualifiedExpression).qualifier
                                        val baseType = baseQualifier?.let { context.getType(it) }
                                        if (baseType is PyMockType) {
                                            val resolved = qualifier.reference?.resolve()
                                            if (resolved is PyFunction) context.getReturnType(resolved) else null
                                        } else null
                                    }
                                }

                                val assignedType = context.getType(value)

                                if (expectedReturnType != null && assignedType != null) {
                                    if (!PyTypeChecker.match(expectedReturnType, assignedType, context)) {
                                        holder.registerProblem(
                                            value,
                                            "Expected type '${expectedReturnType.name}', got '${assignedType.name}' instead"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
