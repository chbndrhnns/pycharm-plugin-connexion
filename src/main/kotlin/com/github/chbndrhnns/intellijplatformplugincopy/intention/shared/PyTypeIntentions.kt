package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

/** Lightweight DTOs shared by intentions. */
data class TypeNames(
    val actual: String?,
    val expected: String?,
    val actualElement: PsiElement?,
    val expectedElement: PsiElement?
)

/** Encapsulates small helpers used by multiple intentions. */
object PyTypeIntentions {
    /** Walk the PSI upwards to find the most suitable expression at the caret. */
    fun findExpressionAtCaret(editor: Editor, file: PsiFile): PyExpression? {
        val offset = editor.caretModel.offset
        val leaf = file.findElementAt(offset) ?: return null

        var current: PsiElement? = leaf
        var bestString: PyExpression? = null
        var bestCall: PyExpression? = null
        var bestParenthesized: PyExpression? = null
        var bestOther: PyExpression? = null

        // Walk up the PSI tree and collect candidates
        while (current != null && current != file) {
            if (current is PyExpression) {
                when (current) {
                    is PyCallExpression -> bestCall = current
                    is PyStringLiteralExpression -> bestString = current
                    is PyParenthesizedExpression -> bestParenthesized = current
                    else -> if (bestOther == null) bestOther = current
                }
            }
            current = current.parent
        }

        // Context-aware prioritization:
        // If we have a string and it's inside a function call argument, prefer the string
        // Otherwise, prefer call expressions for assignment contexts
        if (bestString != null && isInsideFunctionCallArgument(bestString)) {
            return bestString
        }

        // For assignment contexts, prefer call expressions, then parenthesized, then strings, then others
        return bestCall ?: bestParenthesized ?: bestString ?: bestOther
    }

    /**
     * Check if the expression is inside a function call argument (not an assignment).
     */
    private fun isInsideFunctionCallArgument(expr: PyExpression): Boolean {
        // Walk up to see if we're inside a function call argument
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java)
        if (argList != null) {
            val call = PsiTreeUtil.getParentOfType(argList, PyCallExpression::class.java)
            if (call != null && argList.arguments.contains(expr)) {
                // Check if this call is itself in an assignment context AND
                // the expected type context comes from the assignment, not the call parameter
                val assignment = PsiTreeUtil.getParentOfType(call, PyAssignmentStatement::class.java)
                if (assignment != null && assignment.assignedValue == call) {
                    // This call is directly assigned. Check if we have type info from assignment.
                    // If the assignment target has a type annotation, then this is assignment context
                    val hasAssignmentTypeAnnotation = assignment.targets.any { target ->
                        (target as? PyTargetExpression)?.annotation != null
                    }
                    if (hasAssignmentTypeAnnotation) {
                        return false  // This is an assignment context - wrap the call, not the argument
                    }
                }

                // Check if this call is itself a return value
                val returnStmt = PsiTreeUtil.getParentOfType(call, PyReturnStatement::class.java)
                if (returnStmt != null && returnStmt.expression == call) {
                    return false  // This is a return context - wrap the call, not the argument
                }

                return true  // This is truly a function argument context
            }
        }
        return false
    }

    /**
     * Compute short display names for actual/expected types,
     * and ensure expectedElement is correctly set to the type annotation element (if present).
     */
    fun computeTypeNames(expr: PyExpression, ctx: TypeEvalContext): TypeNames {
        val actual = TypeNameRenderer.render(ctx.getType(expr))
        val expectedInfo = getExpectedTypeInfo(expr, ctx)
        val expected = TypeNameRenderer.render(expectedInfo?.type)
        return TypeNames(
            actual = actual,
            expected = expected,
            actualElement = expr,
            expectedElement = expectedInfo?.element
        )
    }

    /**
     * Container for both type and its source element.
     */
    private data class TypeInfo(val type: PyType?, val element: PsiElement?)

    /**
     * Unified logic to get expected type information from surrounding context.
     * Returns both the type and the PSI element that defines it.
     */
    private fun getExpectedTypeInfo(expr: PyExpression, ctx: TypeEvalContext): TypeInfo? {
        // Assignment: x: T = <expr>
        val parent = expr.parent
        if (parent is PyAssignmentStatement) {
            parent.targets.forEach { t ->
                if (t is PyTargetExpression) {
                    val annExpr = t.annotation?.value
                    if (annExpr is PyExpression) {
                        return TypeInfo(ctx.getType(annExpr), annExpr)
                    }
                }
            }
        }

        // Return statement: def f(...) -> T: return <expr>
        if (parent is PyReturnStatement) {
            val fn = PsiTreeUtil.getParentOfType(parent, PyFunction::class.java)
            val retAnnExpr = fn?.annotation?.value
            if (retAnnExpr is PyExpression) {
                return TypeInfo(ctx.getType(retAnnExpr), retAnnExpr)
            }
        }

        // Function call argument: f(<expr>)
        return resolveParamTypeInfo(expr, ctx)
    }

    /**
     * Attempts to resolve parameter type information for the argument expression.
     * Returns both the type and the annotation element.
     */
    private fun resolveParamTypeInfo(expr: PyExpression, ctx: TypeEvalContext): TypeInfo? {
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java) ?: return null
        val call = PsiTreeUtil.getParentOfType(argList, PyCallExpression::class.java) ?: return null

        // Determine argument position ignoring named/keyword args for simplicity
        val args = argList.arguments
        val argIndex = args.indexOfFirst { it == expr }
        if (argIndex < 0) return null

        val calleeExpr = call.callee as? PyReferenceExpression ?: return null
        val resolved = calleeExpr.reference.resolve()

        val pyFunc = resolved as? PyFunction ?: return null
        val params = pyFunc.parameterList.parameters
        if (argIndex >= params.size) return null

        val param = params[argIndex]
        val annValue = (param as? PyNamedParameter)?.annotation?.value
        return if (annValue is PyExpression) {
            // For imports, we need to resolve the annotation reference to get the actual class/type definition
            val resolvedTypeElement = if (annValue is PyReferenceExpression) {
                annValue.reference.resolve()
            } else {
                annValue
            }
            TypeInfo(ctx.getType(annValue), resolvedTypeElement)
        } else {
            null
        }
    }

    /** Pretty name selection used by wrapping intention. */
    fun canonicalCtorName(typeName: String?): String = when (typeName) {
        null, "Unknown" -> "str"
        // keep simple builtins as-is
        "str", "int", "float", "bool", "list", "dict", "set", "tuple" -> typeName
        // If we receive a union/optional string, default to the first non-None-ish item
        else -> typeName
            .substringBefore("|")
            .trim()
            .removeSuffix("]")
            .substringAfterLast('.')
    }

    /** Human-friendly context for messages (e.g., variable names). */
    fun extractContextInfo(expr: PyExpression): String {
        var cur: PsiElement? = expr.parent
        while (cur != null) {
            if (cur is PyAssignmentStatement) {
                val first = cur.targets.firstOrNull() as? PyTargetExpression
                val name = first?.name
                if (!name.isNullOrBlank()) return "variable '$name'"
            }
            cur = cur.parent
        }
        return ""
    }

}
