package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
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

        // Special case: caret on a keyword argument name (e.g., Data(v|al="abc"))
        // In that case, operate on the value expression of the keyword argument.
        PsiTreeUtil.getParentOfType(leaf, PyKeywordArgument::class.java)?.let { kw ->
            val value = kw.valueExpression
            if (value != null && !PsiTreeUtil.isAncestor(value, leaf, false)) {
                return value
            }
        }

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
        if (bestString != null && isInsideFunctionCallArgument(bestString)) {
            return bestString
        }
        // If the caret is within a parenthesized argument (e.g., ("foo")), prefer that argument
        if (bestParenthesized != null && isInsideFunctionCallArgument(bestParenthesized)) {
            // If there's also a string inside, prefer the inner-most string
            return bestString ?: bestParenthesized
        }

        // Otherwise, prefer call expressions for assignment contexts
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
            if (call != null) {
                // Determine whether 'expr' is within any positional or keyword argument
                val inPositional = argList.arguments.any { it == expr || PsiTreeUtil.isAncestor(it, expr, false) }
                val inKeyword = argList.arguments.asSequence()
                    .mapNotNull { it as? PyKeywordArgument }
                    .any { kw ->
                        val v = kw.valueExpression
                        v != null && (v == expr || PsiTreeUtil.isAncestor(v, expr, false))
                    }
                if (inPositional || inKeyword) {
                    // Check if this call is itself in an assignment context AND
                    // the expected type context comes from the assignment, not the call parameter
                    val assignment = PsiTreeUtil.getParentOfType(call, PyAssignmentStatement::class.java)
                    if (assignment != null && assignment.assignedValue == call) {
                        val hasAssignmentTypeAnnotation = assignment.targets.any { target ->
                            (target as? PyTargetExpression)?.annotation != null
                        }
                        if (hasAssignmentTypeAnnotation) {
                            return false  // Assignment context - wrap the call, not the argument
                        }
                    }

                    // Check if this call is itself a return value
                    val returnStmt = PsiTreeUtil.getParentOfType(call, PyReturnStatement::class.java)
                    if (returnStmt != null && returnStmt.expression == call) {
                        return false  // Return context - wrap the call, not the argument
                    }

                    return true  // Truly an argument context
                }
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
        var expected = TypeNameRenderer.render(expectedInfo?.type)
        // Fallback: if expected type couldn't be inferred but we resolved a named element (e.g., class in annotation),
        // use its simple name to drive intention text like "Wrap with OtherStr()".
        if ((expected == "Unknown" || expectedInfo?.type == null) && expectedInfo?.element is PsiNamedElement) {
            val name = (expectedInfo.element as PsiNamedElement).name
            if (!name.isNullOrBlank()) expected = name ?: expected
        }
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
        // Robustly locate the argument index even when the caret expression is nested
        // (e.g., extra parentheses around the literal). Consider an argument a match
        // if it is the same element or an ancestor of the caret expression.
        val argIndex = args.indexOfFirst { it == expr || PsiTreeUtil.isAncestor(it, expr, false) }
        if (argIndex < 0) return null

        val calleeExpr = call.callee as? PyReferenceExpression ?: return null
        val resolved = calleeExpr.reference.resolve()

        // Handle dataclass-like constructor calls: callee resolves to a class, and arguments map to fields
        if (resolved is PyClass) {
            // Prefer keyword argument mapping for robustness
            val keywordArg = (args.getOrNull(argIndex) as? PyKeywordArgument)
                ?: PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java)
            val keywordName = keywordArg?.keyword
            if (!keywordName.isNullOrBlank()) {
                // Try via standard class attribute lookup first
                val classAttr = resolved.findClassAttribute(keywordName, true, ctx)
                var annExpr: PyExpression? = (classAttr as? PyTargetExpression)?.annotation?.value as? PyExpression

                // If not found (e.g., annotation-only dataclass field), scan statement list for annotated targets
                if (annExpr == null) {
                    val stmts = resolved.statementList.statements
                    for (st in stmts) {
                        val targets = PsiTreeUtil.findChildrenOfType(st, PyTargetExpression::class.java)
                        val hit = targets.firstOrNull { it.name == keywordName }
                        val v = hit?.annotation?.value
                        if (v is PyExpression) {
                            annExpr = v
                            break
                        }
                    }
                }

                if (annExpr is PyExpression) {
                    val resolvedTypeElement =
                        if (annExpr is PyReferenceExpression) annExpr.reference.resolve() else annExpr
                    return TypeInfo(ctx.getType(annExpr), resolvedTypeElement)
                }
            }
            // Fallback: no keyword; try positional mapping using class attributes order (best-effort)
            // We attempt to collect annotated class attributes in textual order
            val fields = resolved.classAttributes.filterIsInstance<PyTargetExpression>()
            val annotated = fields.mapNotNull { it.annotation?.value as? PyExpression }
            val targetAnn = annotated.getOrNull(argIndex)
            if (targetAnn != null) {
                val resolvedTypeElement =
                    if (targetAnn is PyReferenceExpression) targetAnn.reference.resolve() else targetAnn
                return TypeInfo(ctx.getType(targetAnn), resolvedTypeElement)
            }
            return null
        }

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
