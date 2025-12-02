package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*

import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Encapsulates the logic for turning the low-level [Target] produced by
 * [TargetDetector] into a higher-level [CustomTypePlan] that the intention can
 * work with.
 */
class PlanBuilder(
    private val detector: TargetDetector = TargetDetector(),
    private val naming: NameSuggester = NameSuggester(),
) {

    fun build(editor: Editor, file: PyFile, context: TypeEvalContext): CustomTypePlan? {
        val detected = detector.find(editor, file, context) ?: return null

        if (shouldIgnoreTarget(detected)) {
            return null
        }

        return when (detected) {
            is AnnotationTarget -> buildFromAnnotation(detected, file)
            is ExpressionTarget -> buildFromExpression(detected, file)
        }
    }

    private fun buildFromAnnotation(target: AnnotationTarget, file: PyFile): CustomTypePlan {
        val preferredName = when {
            target.dataclassField != null && target.builtinName == "list" -> {
                val fieldName = target.ownerName
                fieldName?.let { naming.deriveCollectionBaseName(it) }
                    ?: fieldName?.let { naming.deriveBaseName(it) }
            }

            else -> target.ownerName?.let { id -> naming.deriveBaseName(id) }
        }

        val owner = PsiTreeUtil.getParentOfType(target.annotationRef, PyAnnotationOwner::class.java, false)

        val assignedExpression = run {
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
            targetElement = owner ?: target.dataclassField ?: target.annotationRef,
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
            ?: when {
                target.dataclassField != null && target.builtinName == "list" -> {
                    val fieldName = target.dataclassField.name
                    fieldName?.let { naming.deriveCollectionBaseName(it) }
                        ?: fieldName?.let { naming.deriveBaseName(it) }
                }

                else -> target.dataclassField?.name?.let { naming.deriveBaseName(it) }
            }

        return CustomTypePlan(
            builtinName = target.builtinName,
            annotationRef = target.annotationRef,
            expression = target.expression,
            preferredClassName = preferredName,
            field = target.dataclassField,
            targetElement = target.dataclassField ?: target.annotationRef ?: target.expression,
            sourceFile = file,
        )
    }

    /**
     * Centralized ignore rules for the custom type intention. These mirror the
     * defaults used for the __all__ inspection so that both features behave
     * consistently in test modules and for magic/metadata symbols.
     */
    private fun shouldIgnoreTarget(target: Target): Boolean {
        return when (target) {
            is AnnotationTarget -> shouldIgnoreByName(target.ownerName)
            is ExpressionTarget -> {
                val nameCandidates = listOfNotNull(
                    target.assignmentName,
                    target.parameterName,
                    target.dataclassField?.name,
                )

                if (nameCandidates.any { shouldIgnoreByName(it) }) return true

                // Additionally, skip any expression that lives inside a
                // __all__ assignment like ``__all__ = ["..."]``.
                isInsideDunderAll(target.expression)
            }
        }
    }

    private fun shouldIgnoreByName(name: String?): Boolean {
        name ?: return false

        if (name in IGNORED_EXACT_SYMBOL_NAMES) return true

        return IGNORED_SYMBOL_NAME_PREFIXES.any { prefix -> name.startsWith(prefix) }
    }

    private fun isInsideDunderAll(expression: PyExpression): Boolean {
        val sequence = PsiTreeUtil.getParentOfType(expression, PySequenceExpression::class.java, false)
            ?: return false

        val assignment = PsiTreeUtil.getParentOfType(sequence, PyAssignmentStatement::class.java, false)
            ?: return false

        return assignment.targets.any { it.name == PyNames.ALL }
    }

    companion object {
        private val IGNORED_EXACT_SYMBOL_NAMES = setOf(
            "__all__",
            "__version__",
            "__author__",
            "__doc__",
            "__path__",
            "__annotations__",
        )

        private val IGNORED_SYMBOL_NAME_PREFIXES = listOf(
            "_",       // private / internal
            "test_",  // pytest-style tests
        )
    }
}
