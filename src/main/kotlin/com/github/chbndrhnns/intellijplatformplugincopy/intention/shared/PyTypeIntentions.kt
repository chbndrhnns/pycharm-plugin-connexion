package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

/** Lightweight DTOs shared by intentions. */
data class TypeNames(
    val actual: String?,
    val expected: String?,
    val actualElement: PsiElement?,
    // The PSI element to use for resolving the constructor name via PSI (typically the annotation expression)
    val expectedCtorElement: PyTypedElement?,
    // The resolved named element (class/function) used for import handling, if available
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
        // If we're inside any function call argument (positional or keyword), return the actual argument root expression.
        if ((bestCall != null || bestOther != null) && (bestCall?.let { isInsideFunctionCallArgument(it) } == true || bestOther?.let {
                isInsideFunctionCallArgument(
                    it
                )
            } == true)) {
            val argList = PsiTreeUtil.getParentOfType(leaf, PyArgumentList::class.java)
            val call = PsiTreeUtil.getParentOfType(leaf, PyCallExpression::class.java)
            if (argList != null && call != null) {
                val args = argList.arguments
                // Find argument containing the leaf
                val arg = args.firstOrNull { it == leaf || PsiTreeUtil.isAncestor(it, leaf, false) }
                if (arg is PyKeywordArgument) {
                    arg.valueExpression?.let { return it }
                } else if (arg is PyExpression) {
                    return arg
                }
            }
            // Fallback to call over bare reference
            return bestCall ?: bestOther
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
        val expected = TypeNameRenderer.render(expectedInfo?.type)
        return TypeNames(
            actual = actual,
            expected = expected,
            actualElement = expr,
            expectedCtorElement = expectedInfo?.annotationExpr,
            expectedElement = expectedInfo?.resolvedNamed
        )
    }

    /**
     * Determines the best constructor name to use for wrapping based on expected type information
     * around the given expression. Prefers PSI-based resolution; falls back to type-based unwrapping.
     */
    fun expectedCtorName(expr: PyExpression, ctx: TypeEvalContext): String? {
        val info = getExpectedTypeInfo(expr, ctx) ?: return null

        // First, try PSI-based resolution which also handles text-only union spellings.
        info.annotationExpr?.let { ann ->
            val fromPsi = canonicalCtorName(ann, ctx)
            if (!fromPsi.isNullOrBlank()
                && !fromPsi.equals("UnionType", true)
                && !fromPsi.equals("Union", true)
                && !fromPsi.equals("None", true) // guard against treating None as a constructor
            ) {
                return fromPsi
            }
        }

        // Fallback: use the expected type, unwrap unions/optionals and pick the first non-None class
        val base = info.type?.let { firstNonNoneMember(it) }
        if (base is PyClassType) {
            val name = base.name ?: base.classQName?.substringAfterLast('.')
            return if (name.equals("None", true)) null else name
        }
        val name = base?.name
        return if (name.equals("None", true)) null else name
    }

    /**
     * Container for both type and its source element.
     */
    private data class TypeInfo(
        val type: PyType?,
        // The raw annotation expression, if any (used for PSI-based constructor resolution)
        val annotationExpr: PyTypedElement?,
        // The resolved symbol behind the annotation (class/function), if any (used for import handling)
        val resolvedNamed: PsiNamedElement?
    )

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
                        val named = (annExpr as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
                        // Important: get type from the target element rather than the annotation PSI
                        // so unions like "int | None" are represented as PyUnionType with members.
                        val targetType = ctx.getType(t)
                        return TypeInfo(targetType, annExpr, named)
                    }
                }
            }
        }

        // Return statement: def f(...) -> T: return <expr>
        if (parent is PyReturnStatement) {
            val fn = PsiTreeUtil.getParentOfType(parent, PyFunction::class.java)
            val retAnnExpr = fn?.annotation?.value
            if (retAnnExpr is PyExpression) {
                val named = (retAnnExpr as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
                // Prefer the function's evaluated return type over the raw annotation PSI type.
                val returnType = fn?.let { ctx.getReturnType(it) }
                return TypeInfo(returnType, retAnnExpr, named)
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
                var annExpr: PyExpression? = classAttr?.annotation?.value
                var typeSource: PyTypedElement? = classAttr

                // If not found (e.g., annotation-only dataclass field), scan statement list for annotated targets
                if (annExpr == null) {
                    val stmts = resolved.statementList.statements
                    for (st in stmts) {
                        val targets = PsiTreeUtil.findChildrenOfType(st, PyTargetExpression::class.java)
                        val hit = targets.firstOrNull { it.name == keywordName }
                        val v = hit?.annotation?.value
                        if (v is PyExpression) {
                            annExpr = v
                            typeSource = hit
                            break
                        }
                    }
                }

                if (annExpr is PyExpression) {
                    val named = (annExpr as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
                    // Prefer the evaluated type of the annotated target (field) if available,
                    // it carries the real union members; fall back to the annotation PSI type.
                    val evaluated = typeSource?.let { ctx.getType(it) } ?: ctx.getType(annExpr)
                    return TypeInfo(evaluated, annExpr, named)
                }
            }
            // Fallback: no keyword; try positional mapping using class attributes order (best-effort)
            // We attempt to collect annotated class attributes in textual order
            val fields = resolved.classAttributes.filterIsInstance<PyTargetExpression>()
            val annotated = fields.mapNotNull { it.annotation?.value }
            val targetAnn = annotated.getOrNull(argIndex)
            if (targetAnn != null) {
                val named = (targetAnn as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
                val sourceTarget = fields.getOrNull(argIndex)
                val evaluated = sourceTarget?.let { ctx.getType(it) } ?: ctx.getType(targetAnn)
                return TypeInfo(evaluated, targetAnn, named)
            }
            return null
        }

        val pyFunc = resolved as? PyFunction ?: return null
        val params = pyFunc.parameterList.parameters
        if (argIndex >= params.size) return null

        val param = params[argIndex]
        val annValue = (param as? PyNamedParameter)?.annotation?.value
        // Important: ask the type of the parameter element (not the annotation PSI),
        // because ctx.getType(annotationExpr) may be null for PEP 604 unions.
        val paramType = (param as? PyTypedElement)?.let { ctx.getType(it) }

        // Try to resolve a concrete named element for import handling.
        // 1) If the annotation is a simple reference, resolve it directly.
        // 2) Otherwise, if we have a type, unwrap unions/optionals and take the first non-None class type.
        val resolvedNamed: PsiNamedElement? = when (annValue) {
            is PyReferenceExpression -> annValue.reference.resolve() as? PsiNamedElement
            else -> {
                val base = paramType?.let { firstNonNoneMember(it) }
                (base as? PyClassType)?.pyClass as? PsiNamedElement
            }
        }

        return if (annValue is PyExpression || paramType != null) {
            TypeInfo(
                type = paramType,
                annotationExpr = annValue as? PyTypedElement,
                resolvedNamed = resolvedNamed
            )
        } else {
            null
        }
    }

    /**
     * PSI-based constructor name resolution.
     * - Unwraps unions/optionals via PyUnionType/PyOptionalType
     * - Picks the first non-None branch deterministically
     * - Returns simple class name for classes and builtin names for PyBuiltinType
     */
    fun canonicalCtorName(element: PyTypedElement, ctx: TypeEvalContext): String? {
        val t = ctx.getType(element)
        if (t != null) {
            val base = firstNonNoneMember(t) ?: return null

            // Classes and builtins are represented as PyClassType
            if (base is PyClassType) {
                // Prefer short name; fall back to last component of qualified name
                val name = base.name ?: base.classQName?.substringAfterLast('.')
                return if (name.equals("None", true)) null else name
            }

            // For other types, try generic name
            val name = base.name
            return if (name.equals("None", true)) null else name
        }

        // If there's no resolvable type for the annotation PSI, do not attempt text parsing.
        // We target Python 3.11+, so proper union types should be provided by the type system.
        return null
    }

    /** Unwrap unions and pick the first non-None member. */
    private fun firstNonNoneMember(t: PyType): PyType? {
        if (t is PyUnionType) {
            return t.members.firstOrNull { it != null && !isNoneType(it) }
        }
        return t
    }

    private fun isNoneType(type: PyType?): Boolean {
        if (type == null) return false
        val asClass = type as? PyClassType ?: return false
        val qName = asClass.classQName ?: return false
        // Recognize common forms of None/NoneType fully qualified and short
        return qName.endsWith("NoneType") ||
                qName.equals("None", true) ||
                qName.equals("builtins.None", true) ||
                qName.equals("builtins.NoneType", true)
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
