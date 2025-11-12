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

/** Small value object for constructor selection results. */
data class ExpectedCtor(
    val name: String,
    val symbol: PsiNamedElement?
)

/** Match result used when comparing an element's displayed type with an expected constructor. */
enum class CtorMatch { MATCHES, DIFFERS }

/** Encapsulates small helpers used by multiple intentions. */
object PyTypeIntentions {
    /** Central guard for names that must not be treated as constructors. */
    private fun isNonCtorName(name: String?): Boolean =
        name.isNullOrBlank() || name.equalsAnyIgnoreCase("Union", "UnionType", "None")

    /** Convenience: equals any of the provided options, ignoring case. */
    private fun String.equalsAnyIgnoreCase(vararg options: String): Boolean =
        options.any { this.equals(it, ignoreCase = true) }

    /** Convenience: short name or, if absent, tail of qualified name. */
    private fun PyClassType.shortOrQualifiedTail(): String? =
        this.name ?: this.classQName?.substringAfterLast('.')

    /** Small holder for caret candidates collected while walking up the PSI tree. */
    private data class CaretCandidates(
        val string: PyExpression? = null,
        val call: PyExpression? = null,
        val parenthesized: PyExpression? = null,
        val other: PyExpression? = null,
    )

    /** Collect candidate expressions around caret by walking up the PSI tree once. */
    private fun collectCandidates(leaf: PsiElement, file: PsiFile): CaretCandidates {
        var current: PsiElement? = leaf
        var bestString: PyExpression? = null
        var bestCall: PyExpression? = null
        var bestParenthesized: PyExpression? = null
        var bestOther: PyExpression? = null

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
        return CaretCandidates(bestString, bestCall, bestParenthesized, bestOther)
    }

    /** Choose the most suitable expression for wrapping from collected candidates and context. */
    private fun chooseBest(c: CaretCandidates, leaf: PsiElement): PyExpression? {
        // Prefer string literal when it is an argument
        if (c.string != null && isInsideFunctionCallArgument(c.string)) return c.string

        // Prefer parenthesized argument when applicable, but inner string wins
        if (c.parenthesized != null && isInsideFunctionCallArgument(c.parenthesized))
            return c.string ?: c.parenthesized

        // If inside any call argument, return the real argument root
        val inArg = listOfNotNull(c.call, c.other).any { isInsideFunctionCallArgument(it) }
        if (inArg) {
            val argList = PsiTreeUtil.getParentOfType(leaf, PyArgumentList::class.java)
            val call = PsiTreeUtil.getParentOfType(leaf, PyCallExpression::class.java)
            if (argList != null && call != null) {
                val args = argList.arguments
                val arg = args.firstOrNull { it == leaf || PsiTreeUtil.isAncestor(it, leaf, false) }
                if (arg is PyKeywordArgument) {
                    arg.valueExpression?.let { return it }
                } else if (arg is PyExpression) {
                    return arg
                }
            }
            // Fallback to call over bare reference
            return c.call ?: c.other
        }

        // Generic prioritization outside of argument contexts
        return c.call ?: c.parenthesized ?: c.string ?: c.other
    }

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

        // Prefer the real argument expression only when truly in argument context (not assignment/return)
        argumentRootAtCaret(leaf)?.let { candidate ->
            if (isInsideFunctionCallArgument(candidate)) return candidate
        }

        // Collect candidates once and perform context-aware selection
        val candidates = collectCandidates(leaf, file)
        return chooseBest(candidates, leaf)
    }

    /**
     * If the caret is within a container literal (list/set/tuple/dict), this helper tries to return the
     * concrete element expression that the caret points at. It mirrors the manual selection logic that
     * used to live in the intention and centralizes it for reuse.
     * Returns null when not inside a supported container or a specific element cannot be determined.
     */
    fun findContainerItemAtCaret(editor: Editor, containerOrElement: PyExpression): PyExpression? {
        val offset = editor.caretModel.offset
        return when (containerOrElement) {
            is PyListLiteralExpression -> {
                val elems = containerOrElement.elements
                val exact = elems.firstOrNull { it.textRange.containsOffset(offset) }
                exact ?: run {
                    val right = elems.firstOrNull { it.textRange.startOffset >= offset }
                    val left = elems.lastOrNull { it.textRange.endOffset <= offset }
                    right ?: left
                }
            }

            is PySetLiteralExpression -> {
                val elems = containerOrElement.elements
                val exact = elems.firstOrNull { it.textRange.containsOffset(offset) }
                exact ?: run {
                    val right = elems.firstOrNull { it.textRange.startOffset >= offset }
                    val left = elems.lastOrNull { it.textRange.endOffset <= offset }
                    right ?: left
                }
            }

            is PyTupleExpression -> {
                val elems = containerOrElement.elements
                val exact = elems.firstOrNull { it.textRange.containsOffset(offset) }
                exact ?: run {
                    val right = elems.firstOrNull { it.textRange.startOffset >= offset }
                    val left = elems.lastOrNull { it.textRange.endOffset <= offset }
                    right ?: left
                }
            }

            is PyDictLiteralExpression -> {
                val pairs = containerOrElement.elements
                val exactInPair = pairs.firstOrNull { pair ->
                    val k = pair.key
                    val v = pair.value
                    (k.textRange.containsOffset(offset)) || (v != null && v.textRange.containsOffset(offset))
                }
                when {
                    exactInPair != null -> {
                        val k = exactInPair.key
                        val v = exactInPair.value
                        when {
                            k.textRange.containsOffset(offset) -> k
                            v != null && v.textRange.containsOffset(offset) -> v
                            else -> null
                        }
                    }

                    else -> {
                        // choose nearest pair; prefer right neighbor
                        val right = pairs.firstOrNull { it.textRange.startOffset >= offset }
                        val left = pairs.lastOrNull { it.textRange.endOffset <= offset }
                        val pair = right ?: left
                        // default to key if present
                        (pair?.key ?: pair?.value)
                    }
                }
            }

            else -> null
        }
    }

    /** Returns the argument root expression at caret, resolving keyword arguments to their values. */
    private fun argumentRootAtCaret(leaf: PsiElement): PyExpression? {
        val argList = PsiTreeUtil.getParentOfType(leaf, PyArgumentList::class.java) ?: return null
        val args = argList.arguments
        val arg = args.firstOrNull { it == leaf || PsiTreeUtil.isAncestor(it, leaf, false) }
        return when (arg) {
            is PyKeywordArgument -> arg.valueExpression
            is PyExpression -> arg
            else -> null
        }
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
     * Determines the best constructor name to use for wrapping based on the expected type information
     * around [expr]. Prefers PSI-based resolution (annotation PSI) so we preserve author-written spellings
     * such as `int | None`; falls back to the evaluated type when PSI lacks resolution. Returns a short
     * constructor name (e.g., `str`, `Path`) or null when no meaningful constructor can be determined.
     */
    fun expectedCtorName(expr: PyExpression, ctx: TypeEvalContext): String? {
        val info = getExpectedTypeInfo(expr, ctx) ?: return null

        // First, try PSI-based resolution which also handles text-only union spellings.
        info.annotationExpr?.let { ann ->
            val fromPsi = canonicalCtorName(ann, ctx)
            if (!isNonCtorName(fromPsi)) {
                return fromPsi
            }
        }

        // Fallback: use the expected type, unwrap unions/optionals and pick the first non-None class
        val base = info.type?.let { firstNonNoneMember(it) }
        if (base is PyClassType) {
            val name = base.shortOrQualifiedTail()
            return if (isNonCtorName(name)) null else name
        }
        val name = base?.name
        return if (isNonCtorName(name)) null else name
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
                val returnType = fn.let { ctx.getReturnType(it) }
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

        val args = argList.arguments
        val argIndex = argIndexOf(expr, argList)
        if (argIndex < 0) return null

        val callee = resolvedCallee(call) ?: return null

        return when (callee) {
            is PyClass -> {
                val kw = keywordNameAt(args, argIndex, expr)
                kw?.let { classFieldTypeInfo(callee, it, ctx) }
                    ?: positionalFieldTypeInfo(callee, argIndex, ctx)
            }

            is PyFunction -> {
                val kw = keywordNameAt(args, argIndex, expr)
                functionParamTypeInfo(callee, argList, argIndex, kw, ctx)
            }

            else -> null
        }
    }

    // --- Parameter/field resolution helpers (extracted for clarity) ---
    private fun resolvedCallee(call: PyCallExpression): PsiElement? =
        (call.callee as? PyReferenceExpression)?.reference?.resolve()

    private fun argIndexOf(expr: PyExpression, argList: PyArgumentList): Int =
        argList.arguments.indexOfFirst { it == expr || PsiTreeUtil.isAncestor(it, expr, false) }

    private fun keywordNameAt(args: Array<PyExpression>, index: Int, expr: PyExpression): String? =
        (args.getOrNull(index) as? PyKeywordArgument)?.keyword
            ?: PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java)?.keyword

    private fun classFieldTypeInfo(
        pyClass: PyClass,
        fieldName: String,
        ctx: TypeEvalContext
    ): TypeInfo? {
        val classAttr = pyClass.findClassAttribute(fieldName, true, ctx)
        var annExpr: PyExpression? = classAttr?.annotation?.value
        var typeSource: PyTypedElement? = classAttr

        if (annExpr == null) {
            val stmts = pyClass.statementList.statements
            for (st in stmts) {
                val targets = PsiTreeUtil.findChildrenOfType(st, PyTargetExpression::class.java)
                val hit = targets.firstOrNull { it.name == fieldName }
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
            val evaluated = typeSource?.let { ctx.getType(it) } ?: ctx.getType(annExpr)
            return TypeInfo(evaluated, annExpr, named)
        }
        return null
    }

    private fun positionalFieldTypeInfo(pyClass: PyClass, index: Int, ctx: TypeEvalContext): TypeInfo? {
        val fields = pyClass.classAttributes.filterIsInstance<PyTargetExpression>()
        val annotated = fields.mapNotNull { it.annotation?.value }
        val targetAnn = annotated.getOrNull(index) ?: return null
        val named = (targetAnn as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
        val sourceTarget = fields.getOrNull(index)
        val evaluated = sourceTarget?.let { ctx.getType(it) } ?: ctx.getType(targetAnn)
        return TypeInfo(evaluated, targetAnn, named)
    }

    private fun functionParamTypeInfo(
        pyFunc: PyFunction,
        argList: PyArgumentList,
        argIndex: Int,
        kwName: String?,
        ctx: TypeEvalContext
    ): TypeInfo? {
        val params = pyFunc.parameterList.parameters
        val args = argList.arguments

        val targetParam: PyParameter? = if (!kwName.isNullOrBlank()) {
            params.firstOrNull { (it as? PyNamedParameter)?.name == kwName }
        } else {
            val positionalArgs = args.filter { it !is PyKeywordArgument }
            val posIndex = positionalArgs.indexOf(args.getOrNull(argIndex))
            if (posIndex >= 0) params.getOrNull(posIndex) else null
        }

        val annValue = (targetParam as? PyNamedParameter)?.annotation?.value
        val paramType = (targetParam as? PyTypedElement)?.let { ctx.getType(it) }

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
        } else null
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
                val name = base.shortOrQualifiedTail()
                return if (isNonCtorName(name)) null else name
            }

            // For other types, try generic name
            val name = base.name
            return if (isNonCtorName(name)) null else name
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

    /**
     * If [element] is an item inside a list literal and the list itself has an expected
     * parameterized type like list[T], return the constructor name for T and its resolved element.
     * Currently supports only list literals as a minimal, low-risk addition.
     */
    @Suppress("KDocUnresolvedReference")
    fun tryContainerItemCtor(
        element: PyExpression,
        ctx: TypeEvalContext
    ): ExpectedCtor? {
        val container = findEnclosingContainer(element) ?: return null
        val pos = locatePositionInContainer(element) ?: return null
        return expectedCtorForContainerItem(pos, container, ctx)
    }

    /**
     * Returns true if the actual type of [element] already matches the expected constructor name [expectedCtorName].
     * Used to suppress false-positive suggestions like wrapping an `int` with `int()`.
     */
    @Deprecated(
        "Use elementDisplaysAsCtor and compare against CtorMatch instead",
        ReplaceWith("elementDisplaysAsCtor(element, expectedCtorName, ctx) == CtorMatch.MATCHES")
    )
    fun isElementAlreadyOfCtor(element: PyExpression, expectedCtorName: String, ctx: TypeEvalContext): Boolean {
        val actualName = TypeNameRenderer.render(ctx.getType(element)).lowercase()
        return actualName == expectedCtorName.lowercase()
    }

    /** More explicit variant that returns a match enum rather than a boolean. */
    fun elementDisplaysAsCtor(element: PyExpression, expectedCtorName: String, ctx: TypeEvalContext): CtorMatch {
        val actualName = TypeNameRenderer.render(ctx.getType(element)).lowercase()
        return if (actualName == expectedCtorName.lowercase()) CtorMatch.MATCHES else CtorMatch.DIFFERS
    }

    // --- Container element analysis (generalized) ---
    /**
     * Describes the position of an element within a container literal/comprehension.
     * This allows us to select the correct generic type argument for the element under the caret.
     */
    private sealed interface ContainerPos {
        data object Item : ContainerPos
        data class TupleItem(val index: Int) : ContainerPos
        data object DictKey : ContainerPos
        data object DictValue : ContainerPos
    }

    // Make detection and classification of container cases explicit and discoverable
    /**
     * Classifies supported container PSI nodes we analyze for item-level wrapping suggestions.
     * The analysis is intentionally limited to common literals and comprehensions to keep risk low.
     */
    private sealed interface ContainerKind {
        data object ListLit : ContainerKind
        data object SetLit : ContainerKind
        data class TupleLit(val size: Int) : ContainerKind
        data object DictLit : ContainerKind
        data object ListComp : ContainerKind
        data object SetComp : ContainerKind
        data object DictComp : ContainerKind
    }

    private data class ContainerContext(
        val container: PyExpression,
        val kind: ContainerKind,
        val pos: ContainerPos
    )

    private fun PsiElement.isAncestorOrSelfOf(other: PsiElement): Boolean =
        this == other || PsiTreeUtil.isAncestor(this, other, false)

    /**
     * Finds the nearest enclosing container node (list/set/tuple/dict literal or comprehension).
     * This is a cheap PSI walk upward and does not attempt to resolve arbitrary iterables.
     */
    private fun findNearestContainer(el: PyExpression): PyExpression? =
        PsiTreeUtil.getParentOfType(
            el,
            PyListLiteralExpression::class.java,
            PySetLiteralExpression::class.java,
            PyTupleExpression::class.java,
            PyDictLiteralExpression::class.java,
            PyListCompExpression::class.java,
            PySetCompExpression::class.java,
            PyDictCompExpression::class.java
        )

    private fun classifyContainer(container: PyExpression): ContainerKind = when (container) {
        is PyListLiteralExpression -> ContainerKind.ListLit
        is PySetLiteralExpression -> ContainerKind.SetLit
        is PyTupleExpression -> ContainerKind.TupleLit(container.elements.size)
        is PyDictLiteralExpression -> ContainerKind.DictLit
        is PyListCompExpression -> ContainerKind.ListComp
        is PySetCompExpression -> ContainerKind.SetComp
        is PyDictCompExpression -> ContainerKind.DictComp
        else -> error("Unsupported container: ${container::class.java.simpleName}")
    }

    /**
     * Locates the logical position of [element] within the given [container], e.g. tuple item index,
     * dict key vs value, or generic item for sequence/set types.
     */
    private fun locateElementPosition(container: PyExpression, element: PyExpression): ContainerPos? =
        when (container) {
            is PyListLiteralExpression, is PySetLiteralExpression -> ContainerPos.Item
            is PyTupleExpression -> {
                val idx = container.elements.indexOfFirst { it.isAncestorOrSelfOf(element) }
                if (idx >= 0) ContainerPos.TupleItem(idx) else null
            }

            is PyDictLiteralExpression -> {
                val kv = PsiTreeUtil.getParentOfType(element, PyKeyValueExpression::class.java) ?: return null
                when {
                    kv.key.isAncestorOrSelfOf(element) -> ContainerPos.DictKey
                    kv.value?.isAncestorOrSelfOf(element) == true -> ContainerPos.DictValue
                    else -> null
                }
            }

            is PyListCompExpression, is PySetCompExpression -> ContainerPos.Item
            is PyDictCompExpression -> ContainerPos.DictValue
            else -> null
        }

    private fun analyzeContainer(element: PyExpression): ContainerContext? {
        val c = findNearestContainer(element) ?: return null
        val pos = locateElementPosition(c, element) ?: return null
        val kind = classifyContainer(c)
        return ContainerContext(c, kind, pos)
    }

    private fun findEnclosingContainer(element: PyExpression): PyExpression? =
        analyzeContainer(element)?.container

    private fun locatePositionInContainer(element: PyExpression): ContainerPos? =
        analyzeContainer(element)?.pos

    /**
     * Public-facing helper used by intentions to obtain the constructor for the element type inside
     * a parameterized container. It relies on the surrounding expected type, e.g. `list[T]` or
     * `dict[K, V]`, and picks the appropriate generic argument based on the element position.
     */
    private fun expectedCtorForContainerItem(
        pos: ContainerPos,
        container: PyExpression,
        ctx: TypeEvalContext
    ): ExpectedCtor? {
        val cc = ContainerContext(container, classifyContainer(container), pos)
        return expectedCtorFor(ctx, cc)
    }

    // --- Typing subscription policy (explicit) ---
    /**
     * Captures the arity/shape of typing subscripts we care about.
     * - One   → containers with a single type argument, e.g. list[T], set[T], Iterable[T]
     * - TwoKV → key/value mappings, e.g. dict[K, V], Mapping[K, V]
     * - TupleN(n) → fixed-length tuples, e.g. tuple[T1, T2, ...]
     */
    private sealed interface GenericShape {
        /** list[T], set[T], Iterable[T], ... */
        data object One : GenericShape

        /** dict[K, V], Mapping[K, V], ... */
        data object TwoKV : GenericShape

        /** tuple[T1, T2, ...] */
        data class TupleN(val n: Int) : GenericShape
    }

    private enum class TypingBase { LIST, SET, SEQUENCE, COLLECTION, ITERABLE, MUTABLE_SEQUENCE, TUPLE, DICT, MAPPING, MUTABLE_MAPPING }

    /**
     * Maps lower-cased textual callee names from typing subscripts to a normalized base we support.
     * We intentionally keep this small to avoid surprising matches; new bases can be added as needed.
     */
    private val NAME_TO_BASE: Map<String, TypingBase> = mapOf(
        "list" to TypingBase.LIST,
        "set" to TypingBase.SET,
        "sequence" to TypingBase.SEQUENCE,
        "collection" to TypingBase.COLLECTION,
        "iterable" to TypingBase.ITERABLE,
        "mutablesequence" to TypingBase.MUTABLE_SEQUENCE,
        "tuple" to TypingBase.TUPLE,
        "dict" to TypingBase.DICT,
        "mapping" to TypingBase.MAPPING,
        "mutablemapping" to TypingBase.MUTABLE_MAPPING,
    )

    private fun shapeOf(base: TypingBase, arg: PyExpression): GenericShape = when (base) {
        TypingBase.TUPLE -> {
            val tuple = arg as? PyTupleExpression
            GenericShape.TupleN(tuple?.elements?.size ?: 1)
        }

        TypingBase.DICT, TypingBase.MAPPING, TypingBase.MUTABLE_MAPPING -> GenericShape.TwoKV
        else -> GenericShape.One
    }

    private fun pickTypeArgFor(pos: ContainerPos, shape: GenericShape, arg: PyExpression): PyExpression? =
        when (shape) {
            GenericShape.One -> singleArg(arg)
            GenericShape.TwoKV -> when (pos) {
                is ContainerPos.DictKey -> (arg as? PyTupleExpression)?.elements?.getOrNull(0)
                is ContainerPos.DictValue, ContainerPos.Item -> (arg as? PyTupleExpression)?.elements?.getOrNull(1)
                is ContainerPos.TupleItem -> null
            }

            is GenericShape.TupleN -> when (pos) {
                is ContainerPos.TupleItem -> (arg as? PyTupleExpression)?.elements?.getOrNull(pos.index)
                else -> singleArg(arg)
            }
        }

    private fun expectedCtorFor(
        ctx: TypeEvalContext,
        cc: ContainerContext
    ): ExpectedCtor? {
        val info = getExpectedTypeInfo(cc.container, ctx) ?: return null
        val annExpr = info.annotationExpr as? PyExpression ?: return null
        val sub = annExpr as? PySubscriptionExpression ?: return null

        val baseName = (sub.operand as? PyReferenceExpression)?.name?.lowercase() ?: return null
        val base = NAME_TO_BASE[baseName] ?: return null
        val indexExpr = sub.indexExpression ?: return null
        val shape = shapeOf(base, indexExpr)
        val chosen = pickTypeArgFor(cc.pos, shape, indexExpr) as? PyTypedElement ?: return null

        val ctor = canonicalCtorName(chosen, ctx) ?: return null
        val named = (chosen as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
        return ExpectedCtor(ctor, named)
    }

    private fun singleArg(arg: PyExpression): PyExpression? = when (arg) {
        is PyTupleExpression -> arg.elements.firstOrNull()
        else -> arg
    }

}
