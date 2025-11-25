package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTargetExpression

/**
 * Encapsulates the logic for turning the low-level [Target] produced by
 * [TargetDetector] into a higher-level [CustomTypePlan] that the intention can
 * work with.
 */
class PlanBuilder(
    private val detector: TargetDetector = TargetDetector(),
    private val naming: NameSuggester = NameSuggester(),
) {

    fun build(editor: Editor, file: PyFile): CustomTypePlan? {
        val detected = detector.find(editor, file) ?: return null

        return when (detected) {
            is AnnotationTarget -> buildFromAnnotation(detected, file)
            is ExpressionTarget -> buildFromExpression(detected, file)
        }
    }

    private fun buildFromAnnotation(target: AnnotationTarget, file: PyFile): CustomTypePlan {
        val preferredName = target.ownerName?.let { id -> naming.deriveBaseName(id) }

        val assignedExpression = run {
            val ref = target.annotationRef
            val owner = PsiTreeUtil.getParentOfType(ref, PyAnnotationOwner::class.java, false)

            if (owner is PyAssignmentStatement) {
                return@run owner.assignedValue
            }

            val targetExpr = owner as? PyTargetExpression
            val assignment = targetExpr?.let {
                PsiTreeUtil.getParentOfType(it, PyAssignmentStatement::class.java, false)
            }
            assignment?.assignedValue
        }
        return CustomTypePlan(
            builtinName = target.builtinName,
            annotationRef = target.annotationRef,
            assignedExpression = assignedExpression,
            preferredClassName = preferredName,
            field = target.dataclassField,
            sourceFile = file,
        )
    }

    private fun buildFromExpression(target: ExpressionTarget, file: PyFile): CustomTypePlan {
        val preferredFromKeyword = target.keywordName?.let { naming.deriveBaseName(it) }
        val preferredFromAssignment = target.assignmentName?.let { naming.deriveBaseName(it) }
        val preferredFromParameter = target.parameterName?.let { naming.deriveBaseName(it) }
        val preferredName = preferredFromKeyword
            ?: preferredFromAssignment
            ?: preferredFromParameter
            ?: target.dataclassField?.name?.let { naming.deriveBaseName(it) }

        return CustomTypePlan(
            builtinName = target.builtinName,
            annotationRef = target.annotationRef,
            expression = target.expression,
            preferredClassName = preferredName,
            field = target.dataclassField,
            sourceFile = file,
        )
    }
}
