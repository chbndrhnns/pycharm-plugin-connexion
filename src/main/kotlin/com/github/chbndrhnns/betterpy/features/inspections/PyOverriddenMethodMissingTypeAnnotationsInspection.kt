package com.github.chbndrhnns.betterpy.features.inspections

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.intentions.annotations.CopyTypeAnnotationsFromParentSupport
import com.intellij.codeInspection.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction

class PyOverriddenMethodMissingTypeAnnotationsInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        if (!PluginSettingsState.instance().state.enableOverriddenMethodMissingTypeAnnotationsInspection) {
            return object : PyElementVisitor() {}
        }

        return object : PyElementVisitor() {
            override fun visitPyFunction(node: PyFunction) {
                super.visitPyFunction(node)
                if (node.containingClass == null) return

                val plan = CopyTypeAnnotationsFromParentSupport.buildCopyPlan(node) ?: return
                if (plan.source == node) return
                val targetPlan = plan.targets.firstOrNull { it.target == node } ?: return

                if (targetPlan.parameters.isEmpty() && targetPlan.returnAnnotation == null) return

                var hasMissing = false
                var hasConflicts = false
                for (update in targetPlan.parameters) {
                    val existing = update.parameter.annotation?.value?.text
                    if (existing == null) {
                        hasMissing = true
                    } else {
                        hasConflicts = true
                    }
                }
                if (targetPlan.returnAnnotation != null) {
                    val existingReturn = node.annotation?.value?.text
                    if (existingReturn == null) {
                        hasMissing = true
                    } else {
                        hasConflicts = true
                    }
                }

                val anchor = node.nameIdentifier ?: return

                val highlightType = if (hasMissing) {
                    ProblemHighlightType.WEAK_WARNING
                } else {
                    ProblemHighlightType.INFORMATION
                }
                val description = if (hasMissing) {
                    "Override is missing type annotations from parent method"
                } else {
                    "Override type annotations differ from parent method"
                }
                holder.registerProblem(
                    anchor,
                    description,
                    highlightType,
                    CopyParentTypeAnnotationsFix(node)
                )
            }
        }
    }

    private class CopyParentTypeAnnotationsFix(function: PyFunction) : LocalQuickFix {
        private val functionPointer: SmartPsiElementPointer<PyFunction> =
            SmartPointerManager.getInstance(function.project).createSmartPsiElementPointer(function)

        override fun getFamilyName(): String =
            PluginConstants.ACTION_PREFIX + "Copy type annotations from parent"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val function = functionPointer.element ?: return
            if (ApplicationManager.getApplication().isWriteAccessAllowed) {
                ApplicationManager.getApplication().invokeLater {
                    applyFixInternal(project)
                }
                return
            }

            applyFixInternal(project)
        }

        private fun applyFixInternal(project: Project) {
            val function = functionPointer.element ?: return
            val plan = ReadAction.compute<CopyTypeAnnotationsFromParentSupport.CopyPlan?, RuntimeException> {
                CopyTypeAnnotationsFromParentSupport.buildCopyPlan(function)
            } ?: return
            if (plan.source == function) return

            if (!CopyTypeAnnotationsFromParentSupport.showConflictsDialog(project, plan.conflicts, familyName)) {
                return
            }

            WriteCommandAction.runWriteCommandAction(project, familyName, null, {
                CopyTypeAnnotationsFromParentSupport.applyPlan(project, plan)
            }, function.containingFile)
        }
    }
}
