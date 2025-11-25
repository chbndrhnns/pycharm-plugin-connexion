package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression

/**
 * Describes what the "Introduce custom type from stdlib" intention is
 * operating on. This replaces the inner Target data class in the intention
 * and makes the different entry points (annotation vs expression) explicit.
 */
sealed interface Target {
    val builtinName: String
}

data class AnnotationTarget(
    override val builtinName: String,
    val annotationRef: PyReferenceExpression,
    val ownerName: String?,
    val dataclassField: PyTargetExpression?,
) : Target

data class ExpressionTarget(
    override val builtinName: String,
    val expression: PyExpression,
    val annotationRef: PyReferenceExpression?,
    val keywordName: String?,
    val assignmentName: String?,
    val parameterName: String?,
    val dataclassField: PyTargetExpression?,
) : Target
