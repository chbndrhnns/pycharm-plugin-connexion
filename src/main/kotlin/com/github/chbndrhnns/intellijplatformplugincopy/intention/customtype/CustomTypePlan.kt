package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTargetExpression

/**
 * Immutable description of what the "Introduce custom type from stdlib" flow
 * should do for a single invocation.
 *
 * For now this is a thin wrapper around the information previously held in the
 * inner Target data class of the intention plus the originating [sourceFile].
 * More execution details (like chosen target file or final type name) can be
 * added later without changing the intention's public behaviour.
 */
data class CustomTypePlan(
    val builtinName: String,
    val annotationRef: PyExpression? = null,
    val expression: PyExpression? = null,
    val assignedExpression: PyExpression? = null,
    val preferredClassName: String? = null,
    val field: PyTargetExpression? = null,
    /**
     * PSI element representing the logical declaration/usage this plan operates on.
     *
     * Used e.g. for checking blocking inspections across the whole construct rather
     * than just at the caret offset.
     */
    val targetElement: PsiElement? = null,
    val sourceFile: PyFile,
    /**
     * The kind of custom type to generate (subclass, NewType, frozen dataclass, etc.).
     *
     * Defaults to [CustomTypeKind.SUBCLASS] for backward compatibility with existing
     * behavior.
     */
    val typeKind: CustomTypeKind = CustomTypeKind.DEFAULT,
)
