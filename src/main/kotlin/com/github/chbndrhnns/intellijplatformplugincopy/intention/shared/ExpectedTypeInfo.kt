package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.*

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
        val actualType = TypeNameRenderer.getAnnotatedType(expr, ctx) ?: ctx.getType(expr)
        val actual = TypeNameRenderer.render(actualType)

        val expectedInfo = getExpectedTypeInfo(expr, ctx)

        val expectedType =
            expectedInfo?.annotationExpr?.let { TypeNameRenderer.getAnnotatedType(it, ctx) } ?: expectedInfo?.type
        val expected = TypeNameRenderer.render(expectedType)
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
        if (element is PyStringLiteralExpression) {
            return element.stringValue
        }

        val t = TypeNameRenderer.getAnnotatedType(element, ctx) ?: ctx.getType(element)
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


    fun elementDisplaysAsCtor(element: PyExpression, expectedCtorName: String, ctx: TypeEvalContext): CtorMatch {
        if (expectedCtorName.equals("object", ignoreCase = true)) return CtorMatch.MATCHES
        val actualName = TypeNameRenderer.render(ctx.getType(element)).lowercase()
        return if (actualName == expectedCtorName.lowercase()) CtorMatch.MATCHES else CtorMatch.DIFFERS
    }

    // ---- Exposed for ContainerTyping ----
    fun getExpectedTypeInfo(expr: PyExpression, ctx: TypeEvalContext): TypeInfo? =
        doGetExpectedTypeInfo(expr, ctx)

    // ---- Internal implementation ----

    private fun isNonCtorName(name: String?): Boolean =
        name.isNullOrBlank() || name.equalsAnyIgnoreCase("Union", "UnionType", "None", "object", "Any")

    private fun String.equalsAnyIgnoreCase(vararg options: String): Boolean =
        options.any { this.equals(it, ignoreCase = true) }

    private fun PyClassType.shortOrQualifiedTail(): String? =
        this.name ?: this.classQName?.substringAfterLast('.')

    private fun doGetExpectedTypeInfo(expr: PyExpression, ctx: TypeEvalContext): TypeInfo? {
        val parent = expr.parent

        if (parent is PyParenthesizedExpression) {
            return doGetExpectedTypeInfo(parent, ctx)
        }

        // Support walrus operator
        if (parent is PyAssignmentExpression && parent.assignedValue == expr) {
            return doGetExpectedTypeInfo(parent, ctx)
        }

        // Support *args
        if (parent is PyStarArgument) {
            val facade = PyPsiFacade.getInstance(parent.project)
            val iterableClass = facade.createClassByQName("typing.Iterable", parent)
                ?: facade.createClassByQName("collections.abc.Iterable", parent)
                ?: facade.createClassByQName("list", parent)

            if (iterableClass != null) {
                return TypeInfo(ctx.getType(iterableClass), null, iterableClass)
            }
            return null
        }

        // Support default values in function parameters
        if (parent is PyParameter && parent.defaultValue == expr) {
            val annExpr = (parent as? PyNamedParameter)?.annotation?.value
            if (annExpr is PyExpression) {
                val named = (annExpr as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
                val type = ctx.getType(annExpr)
                return TypeInfo(type, annExpr, named)
            }
        }

        // Support lambda bodies
        if (parent is PyLambdaExpression && parent.body == expr) {
            // Recursively determine the expected type of the lambda expression itself
            val lambdaInfo = doGetExpectedTypeInfo(parent, ctx)
            val lambdaType = lambdaInfo?.type
            if (lambdaType is PyCallableType) {
                val retType = lambdaType.getReturnType(ctx)
                if (retType != null) {
                    return TypeInfo(retType, null, null)
                }
            }
        }

        // Support yield expressions
        if (parent is PyYieldExpression && parent.expression == expr) {
            val fn = PsiTreeUtil.getParentOfType(parent, PyFunction::class.java)
            val retAnn = fn?.annotation?.value

            if (parent.isDelegating) {
                val facade = PyPsiFacade.getInstance(parent.project)
                val iterableClass = facade.createClassByQName("typing.Iterable", parent)
                    ?: facade.createClassByQName("collections.abc.Iterable", parent)
                    ?: facade.createClassByQName("list", parent)
                if (iterableClass != null) {
                    return TypeInfo(ctx.getType(iterableClass), null, iterableClass)
                }
            }

            if (retAnn is PySubscriptionExpression) {
                // Generator[Yield, Send, Return] or Iterator[Yield] or Iterable[Yield]
                val qName = (retAnn.operand as? PyReferenceExpression)?.reference?.resolve()
                val name =
                    (qName as? PyQualifiedNameOwner)?.qualifiedName ?: (retAnn.operand as? PyReferenceExpression)?.name

                // Simple heuristic: first type argument is the yield type for Generator/Iterator
                val index = 0
                val yieldTypeExpr = retAnn.indexExpression.let {
                    // indexExpression might be a TupleExpression if multiple args
                    (it as? PyTupleExpression)?.elements?.getOrNull(index) ?: if (index == 0) it else null
                }

                if (yieldTypeExpr != null) {
                    val named = (yieldTypeExpr as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
                    val type = ctx.getType(yieldTypeExpr)
                    return TypeInfo(type, yieldTypeExpr, named)
                }
            }
        }

        if (parent is PyKeyValueExpression) {
            val dict = PsiTreeUtil.getParentOfType(parent, PyDictLiteralExpression::class.java)
            if (dict != null) {
                val dictInfo = doGetExpectedTypeInfo(dict, ctx)
                val dictType = dictInfo?.type
                if (dictType is PyCollectionType && (dictType.name == "dict" || dictType.name == "Dict")) {
                    val type = when (expr) {
                        parent.key -> dictType.elementTypes.getOrNull(0)
                        parent.value -> dictType.elementTypes.getOrNull(1)
                        else -> null
                    }
                    if (type != null) {
                        val resolved = (type as? PyClassType)?.pyClass
                        return TypeInfo(type, null, resolved)
                    }
                }
            }
        }

        if (parent is PyAssignmentStatement) {
            parent.targets.forEach { t ->
                if (t is PyTargetExpression) {
                    var annExpr = t.annotation?.value
                    if (annExpr == null) {
                        val targetName = t.name
                        if (targetName != null) {
                            val decl = findTypeDeclaration(parent, targetName)
                            annExpr = decl?.annotation?.value
                        }
                    }

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

        if ((parent is PyIfPart && parent.condition == expr) ||
            (parent is PyWhilePart && parent.condition == expr) ||
            (parent is PyConditionalExpression && parent.condition == expr)
        ) {
            val builtinCache = com.jetbrains.python.psi.impl.PyBuiltinCache.getInstance(parent)
            val objectType = builtinCache.objectType
            if (objectType != null) {
                return TypeInfo(objectType, null, builtinCache.getClass("object"))
            }
        }

        return resolveParamTypeInfo(expr, ctx)
    }

    private fun findTypeDeclaration(start: PsiElement, targetName: String): PyTypeDeclarationStatement? {
        var element = start.prevSibling
        while (element != null) {
            if (element is PyTypeDeclarationStatement) {
                val target = element.target
                if (target is PyTargetExpression && target.name == targetName) {
                    return element
                }
            }
            element = element.prevSibling
        }
        return null
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

                // Try class fields (TypedDict, etc.)
                val fieldInfo = kw?.let { classFieldTypeInfo(callee, it, ctx) }
                if (fieldInfo != null) return fieldInfo

                // For class constructors (Client(val=...)), prefer __init__ parameter info if available.
                val init = callee.findInitOrNew(true, ctx)
                if (init is PyFunction) {
                    // Only handle keyword arguments for now as positional mapping needs
                    // implicit 'self' adjustment which functionParamTypeInfo doesn't support yet.
                    if (kw != null) {
                        val fromInit = functionParamTypeInfo(init, argList, argIndex, kw, ctx)
                        if (fromInit != null) return fromInit
                    }
                }

                null
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
        ctx: TypeEvalContext,
        offset: Int = 0
    ): TypeInfo? {
        val params = pyFunc.parameterList.parameters
        val args = argList.arguments

        val targetParam: PyParameter? = if (!kwName.isNullOrBlank()) {
            params.firstOrNull { (it as? PyNamedParameter)?.name == kwName }
        } else {
            val positionalArgs = args.filter { it !is PyKeywordArgument }
            val posIndex = positionalArgs.indexOf(args.getOrNull(argIndex))
            if (posIndex >= 0) params.getOrNull(posIndex + offset) else null
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
