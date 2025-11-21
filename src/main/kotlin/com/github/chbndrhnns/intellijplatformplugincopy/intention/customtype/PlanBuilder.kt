package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.editor.Editor
import com.jetbrains.python.psi.PyFile

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
                CustomTypePlan(
                    builtinName = detected.builtinName,
                    annotationRef = detected.annotationRef,
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
                    expression = detected.expression,
                    preferredClassName = preferredName,
                    field = detected.dataclassField,
                    sourceFile = file,
                )
            }
        }
    }
}
