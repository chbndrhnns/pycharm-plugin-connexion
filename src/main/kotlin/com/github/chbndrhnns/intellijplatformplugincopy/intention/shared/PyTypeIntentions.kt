package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

/** Lightweight DTOs shared by intentions. */
data class TypeNames(val actual: String?, val expected: String?)

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

        // Prioritize call expressions, then parenthesized, then strings, then others
        return bestCall ?: bestParenthesized ?: bestString ?: bestOther
    }

    /** Compute short display names for actual/expected types. */
    fun computeTypeNames(expr: PyExpression, ctx: TypeEvalContext): TypeNames {
        val actual = TypeNameRenderer.render(ctx.getType(expr))
        val expected = TypeNameRenderer.render(getExpectedTypeFromContext(expr, ctx))
        return TypeNames(actual, expected)
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

    /**
     * Shared logic to infer the expected type from surrounding context.
     * - From assignment target annotations
     * - From call-argument position (best-effort)
     */
    fun getExpectedTypeFromContext(expr: PyExpression, ctx: TypeEvalContext): PyType? {
        // Assignment: x: T = <expr>
        val parent = expr.parent
        if (parent is PyAssignmentStatement) {
            parent.targets.forEach { t ->
                if (t is PyTargetExpression) {
                    val annExpr = t.annotation?.value
                    if (annExpr is PyExpression) return ctx.getType(annExpr)
                }
            }
        }

        // Return statement: def f(...) -> T: return <expr>
        if (parent is PyReturnStatement) {
            val fn = PsiTreeUtil.getParentOfType(parent, PyFunction::class.java)
            val retAnnExpr = fn?.annotation?.value
            if (retAnnExpr is PyExpression) return ctx.getType(retAnnExpr)
        }

        // Function call argument: f(<expr>)
        resolveParamType(expr, ctx)?.let { return it }

        return null
    }

    /**
     * Attempts to resolve the parameter's annotated type for the argument expression.
     * This is a lightweight, best-effort resolution that:
     * 1) Finds the surrounding PyCallExpression and the argument index of [expr].
     * 2) Resolves the callee to a PyFunction when possible.
     * 3) If a parameter exists at that index and has an annotation, returns its type via [TypeEvalContext].
     *
     * It intentionally ignores kwargs, *args, **kwargs and complex matching rules to keep the implementation small.
     */
    fun resolveParamType(expr: PyExpression, ctx: TypeEvalContext): PyType? {
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
        return if (annValue is PyExpression) ctx.getType(annValue) else null
    }
}
