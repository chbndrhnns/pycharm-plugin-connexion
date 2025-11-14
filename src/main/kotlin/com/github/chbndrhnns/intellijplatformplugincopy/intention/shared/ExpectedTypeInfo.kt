package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

// Exposed to sibling module ContainerTyping within the same source set.
internal data class TypeInfo(
    val type: PyType?,
    val annotationExpr: PyTypedElement?,
    val resolvedNamed: PsiNamedElement?
)

/** Type information discovery and constructor-name resolution. */
internal object ExpectedTypeInfo {

    // ---- Public API used by facade and other modules ----
    fun computeDisplayTypeNames(expr: PyExpression, ctx: TypeEvalContext): TypeNames {
        val actual = TypeNameRenderer.render(ctx.getType(expr))
        val expectedInfo = getExpectedTypeInfo(expr, ctx)
        var expected = TypeNameRenderer.render(expectedInfo?.type)
        if (expected == "Unknown") {
            // Fall back to annotation text (e.g., forward ref strings) when evaluator can’t render
            val ann = expectedInfo?.annotationExpr
            if (ann != null) canonicalCtorName(ann, ctx)?.let { expected = it }
        }
        return TypeNames(
            actual = actual,
            expected = expected,
            actualElement = expr,
            expectedAnnotationElement = expectedInfo?.annotationExpr,
            expectedElement = expectedInfo?.resolvedNamed
        )
    }

    fun expectedCtorName(expr: PyExpression, ctx: TypeEvalContext): String? {
        val info = getExpectedTypeInfo(expr, ctx) ?: return null
        info.annotationExpr?.let { ann ->
            val fromPsi = canonicalCtorName(ann, ctx)
            if (!isNonCtorName(fromPsi)) return fromPsi
        }

        val base = info.type?.let { firstNonNoneMember(it) }
        if (base is PyClassType) {
            val name = base.shortOrQualifiedTail()
            return if (isNonCtorName(name)) null else name
        }
        val name = base?.name
        return if (isNonCtorName(name)) null else name
    }

    fun canonicalCtorName(element: PyTypedElement, ctx: TypeEvalContext): String? {
        // If the typed element is a string literal annotation (forward ref), use its textual name
        (element as? PyStringLiteralExpression)?.let { str ->
            val raw = str.stringValue
            val name = raw?.substringAfterLast('.')
            if (!name.isNullOrBlank() && !isNonCtorName(name)) return name
        }

        val t = ctx.getType(element)
        if (t != null) {
            val base = firstNonNoneMember(t) ?: return null
            if (base is PyClassType) {
                val name = base.shortOrQualifiedTail()
                return if (isNonCtorName(name)) null else name
            }
            val name = base.name
            return if (isNonCtorName(name)) null else name
        }
        return null
    }

    fun isElementAlreadyOfCtor(element: PyExpression, expectedCtorName: String, ctx: TypeEvalContext): Boolean {
        val actualName = TypeNameRenderer.render(ctx.getType(element)).lowercase()
        return actualName == expectedCtorName.lowercase()
    }

    fun elementDisplaysAsCtor(element: PyExpression, expectedCtorName: String, ctx: TypeEvalContext): CtorMatch {
        val actualName = TypeNameRenderer.render(ctx.getType(element)).lowercase()
        return if (actualName == expectedCtorName.lowercase()) CtorMatch.MATCHES else CtorMatch.DIFFERS
    }

    // ---- Exposed for ContainerTyping ----
    fun getExpectedTypeInfo(expr: PyExpression, ctx: TypeEvalContext): TypeInfo? =
        doGetExpectedTypeInfo(expr, ctx)

    // ---- Internal implementation ----

    private fun isNonCtorName(name: String?): Boolean =
        name.isNullOrBlank() || name.equalsAnyIgnoreCase("Union", "UnionType", "None")

    private fun String.equalsAnyIgnoreCase(vararg options: String): Boolean =
        options.any { this.equals(it, ignoreCase = true) }

    private fun PyClassType.shortOrQualifiedTail(): String? =
        this.name ?: this.classQName?.substringAfterLast('.')

    private fun doGetExpectedTypeInfo(expr: PyExpression, ctx: TypeEvalContext): TypeInfo? {
        val parent = expr.parent
        if (parent is PyAssignmentStatement) {
            parent.targets.forEach { t ->
                if (t is PyTargetExpression) {
                    val annExpr = t.annotation?.value
                    if (annExpr is PyExpression) {
                        val named = (annExpr as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
                        val targetType = ctx.getType(t)
                        return TypeInfo(targetType, annExpr, named)
                    }
                }
            }
        }

        if (parent is PyReturnStatement) {
            val fn = PsiTreeUtil.getParentOfType(parent, PyFunction::class.java)
            val retAnnExpr = fn?.annotation?.value
            if (retAnnExpr is PyExpression) {
                val named = (retAnnExpr as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
                val returnType = fn.let { ctx.getReturnType(it) }
                return TypeInfo(returnType, retAnnExpr, named)
            }
        }

        return resolveParamTypeInfo(expr, ctx)
    }

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
        return qName.endsWith("NoneType") ||
                qName.equals("None", true) ||
                qName.equals("builtins.None", true) ||
                qName.equals("builtins.NoneType", true)
    }
}
