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
            is AnnotationTarget -> {
                val preferredName = detected.ownerName?.let { id -> naming.deriveBaseName(id) }

                val assignedExpression = run {
                    val ref = detected.annotationRef
                    val owner = PsiTreeUtil.getParentOfType(ref, PyAnnotationOwner::class.java, false)

                    if (owner is PyAssignmentStatement) {
                        return@run owner.assignedValue
                    }

                    val target = owner as? PyTargetExpression
                    val assignment = target?.let {
                        PsiTreeUtil.getParentOfType(it, PyAssignmentStatement::class.java, false)
                    }
                    assignment?.assignedValue
                }
                CustomTypePlan(
                    builtinName = detected.builtinName,
                    annotationRef = detected.annotationRef,
                    assignedExpression = assignedExpression,
                    preferredClassName = preferredName,
                    field = detected.dataclassField,
                    sourceFile = file,
                )
            }

            is ExpressionTarget -> {
                val preferredFromKeyword = detected.keywordName?.let { naming.deriveBaseName(it) }
                val preferredFromAssignment = detected.assignmentName?.let { naming.deriveBaseName(it) }
                val preferredName = preferredFromKeyword
                    ?: preferredFromAssignment
                    ?: detected.dataclassField?.name?.let { naming.deriveBaseName(it) }

                CustomTypePlan(
                    builtinName = detected.builtinName,
                    annotationRef = detected.annotationRef,
                    expression = detected.expression,
                    preferredClassName = preferredName,
                    field = detected.dataclassField,
                    sourceFile = file,
                )
            }
        }
    }
}
