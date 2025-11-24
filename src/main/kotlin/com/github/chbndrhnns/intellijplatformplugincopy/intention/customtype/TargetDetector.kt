package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Extracted logic for locating an intention target at the caret. This is
 * largely a mechanical move from the original intention class so behaviour
 * stays identical.
 */
class TargetDetector {

    fun find(editor: Editor, file: PyFile): Target? {
        val offset = editor.caretModel.offset
        val leaf = file.findElementAt(offset) ?: return null

        tryFromAnnotation(leaf)?.let { return it }
        return tryFromExpression(editor, file, leaf)
    }

    private fun tryFromAnnotation(leaf: PsiElement): AnnotationTarget? {
        val ref = PsiTreeUtil.getParentOfType(leaf, PyReferenceExpression::class.java, false) ?: return null
        val name = ref.name ?: return null

        val normalizedName = normalizeName(name, ref) ?: return null

        val annotation = PsiTreeUtil.getParentOfType(ref, PyAnnotation::class.java, false) ?: return null
        val owner = PsiTreeUtil.getParentOfType(ref, PyAnnotationOwner::class.java, true)

        var dataclassField: PyTargetExpression? = null
        val identifier = when (owner) {
            is PyTargetExpression -> {
                dataclassField = owner.takeIf { isDataclassField(it) }
                owner.name
            }

            is PyNamedParameter -> owner.name
            is PyTypeDeclarationStatement -> {
                val firstTarget = PsiTreeUtil.getChildOfType(owner, PyTargetExpression::class.java)
                dataclassField = firstTarget?.takeIf { isDataclassField(it) }
                firstTarget?.name
            }

            else -> null
        }

        return AnnotationTarget(
            builtinName = normalizedName,
            annotationRef = ref,
            ownerName = identifier,
            dataclassField = dataclassField,
        )
    }

    private fun normalizeName(name: String, ref: PsiElement? = null): String? {
        if (name in SUPPORTED_BUILTINS) return name

        // Check simple mapping first (e.g. typing.Dict -> dict)
        TYPING_ALIASES[name]?.let { return it }

        // If we have a reference, try to resolve it to check if it comes from typing
        if (ref is PyReferenceExpression && name in TYPING_SHORT_NAMES) {
            val context = TypeEvalContext.codeAnalysis(ref.project, ref.containingFile)
            val resolved = ref.reference.resolve()
            if (isTypingSymbol(resolved)) {
                return TYPING_ALIASES[name]
            }
        }

        return null
    }

    private fun isTypingSymbol(element: PsiElement?): Boolean {
        if (element == null) return false
        // Simple check: is it in a file named "typing.py" or "typing.pyi"?
        // This is a heuristic but standard enough for PyCharm plugin logic often
        val file = element.containingFile
        return file is PyFile && (file.name == "typing.py" || file.name == "typing.pyi")
    }

    private fun tryFromExpression(editor: Editor, file: PyFile, leaf: PsiElement): ExpressionTarget? {
        val exprFromCaret = PyTypeIntentions.findExpressionAtCaret(editor, file)
        // Refine target if the caret selected a container (like dict/list in a function call)
        // but we actually want the item inside it.
        val refinedExpr = exprFromCaret?.let {
            PyTypeIntentions.findContainerItemAtCaret(editor, it)
        } ?: exprFromCaret

        val expr = refinedExpr
            ?: PsiTreeUtil.getParentOfType(leaf, PyStringLiteralExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(leaf, PyNumericLiteralExpression::class.java, false)
            ?: return null

        val ctx = TypeEvalContext.codeAnalysis(file.project, file)
        val typeNames = PyTypeIntentions.computeDisplayTypeNames(expr, ctx)


        val expectedClass = typeNames.expectedElement as? PyClass
        val isTypingAlias = expectedClass != null &&
                (expectedClass.name in TYPING_SHORT_NAMES && isTypingSymbol(expectedClass))

        if (expectedClass != null && expectedClass.name !in SUPPORTED_BUILTINS && !isTypingAlias) {
            val supers = expectedClass.getSuperClasses(ctx)
            if (supers.any { it.name in SUPPORTED_BUILTINS }) {
                return null
            }
        }

        var candidate: String? = typeNames.expected ?: typeNames.actual
        var normalizedCandidate = candidate?.let { normalizeName(it) }

        if (normalizedCandidate == null) {
            normalizedCandidate = when (expr) {
                is PyStringLiteralExpression -> "str"
                is PyNumericLiteralExpression -> if (expr.isIntegerLiteral) "int" else "float"
                else -> null
            }
        }

        normalizedCandidate ?: return null

        // Logic check: if we normalized it, it is supported.
        // But strictly check if it is in supported builtins now (normalized name should be)
        if (normalizedCandidate !in SUPPORTED_BUILTINS) return null

        if (isAlreadyWrappedInCustomType(expr)) return null

        val preferredNameFromKeyword = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)
            ?.takeIf { it.valueExpression == expr }
            ?.keyword

        var annotationRef: PyReferenceExpression? = null
        val preferredNameFromAssignment = if (preferredNameFromKeyword == null) {
            val assignment = PsiTreeUtil.getParentOfType(expr, PyAssignmentStatement::class.java, false)
            if (assignment != null) {
                val firstTarget = assignment.targets.firstOrNull() as? PyTargetExpression
                val annotation = firstTarget?.annotation
                val annotationValue = annotation?.value as? PyReferenceExpression
                val annName = annotationValue?.name
                if (annName != null && (annName == candidate || normalizeName(
                        annName,
                        annotationValue
                    ) == normalizedCandidate)
                ) {
                    annotationRef = annotationValue
                }
                firstTarget?.name
            } else {
                null
            }
        } else null

        val dataclassFieldFromExpr = findDataclassFieldForExpression(expr)

        if (dataclassFieldFromExpr != null) {
            val typeDecl =
                PsiTreeUtil.getParentOfType(dataclassFieldFromExpr, PyTypeDeclarationStatement::class.java, false)
            val annExpr = typeDecl?.annotation?.value
            val annRef = annExpr as? PyReferenceExpression
            val annName = annRef?.name
            val normalizedAnn = annName?.let { normalizeName(it, annRef) }

            if (normalizedAnn != null && normalizedAnn !in SUPPORTED_BUILTINS) {
                return null
            }
        }

        return ExpressionTarget(
            builtinName = normalizedCandidate,
            expression = expr,
            annotationRef = annotationRef,
            keywordName = preferredNameFromKeyword,
            assignmentName = preferredNameFromAssignment,
            dataclassField = dataclassFieldFromExpr,
        )
    }

    private fun isAlreadyWrappedInCustomType(expr: PyExpression): Boolean {
        val kwArg = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)
        val argExpr: PyExpression = when {
            kwArg != null && kwArg.valueExpression == expr -> kwArg
            else -> expr
        }

        val argList = PsiTreeUtil.getParentOfType(argExpr, PyArgumentList::class.java, false) ?: return false
        val call = argList.parent as? PyCallExpression ?: return false
        val calleeRef = call.callee as? PyReferenceExpression ?: return false
        val resolved = calleeRef.reference.resolve()
        val resolvedClass = resolved as? PyClass ?: return false
        if (isDataclassClass(resolvedClass)) return false
        val className = resolvedClass.name ?: return false
        return className !in SUPPORTED_BUILTINS
    }

    private fun isDataclassField(field: PyTargetExpression): Boolean {
        val pyClass = PsiTreeUtil.getParentOfType(field, PyClass::class.java) ?: return false
        return isDataclassClass(pyClass)
    }


    private fun findDataclassFieldForExpression(expr: PyExpression): PyTargetExpression? {
        val kwArg = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)
        if (kwArg != null && kwArg.valueExpression == expr) {
            val call = PsiTreeUtil.getParentOfType(kwArg, PyCallExpression::class.java, true) ?: return null
            val callee = call.callee as? PyReferenceExpression ?: return null
            val resolved = callee.reference.resolve() as? PyClass ?: return null
            if (!isDataclassClass(resolved)) return null
            val fieldName = kwArg.keyword ?: return null
            return resolved.classAttributes.firstOrNull { it.name == fieldName }
        }

        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java, false) ?: return null
        val call = argList.parent as? PyCallExpression ?: return null
        val callee = call.callee as? PyReferenceExpression ?: return null
        val resolved = callee.reference.resolve() as? PyClass ?: return null
        if (!isDataclassClass(resolved)) return null

        val args = argList.arguments.toList()
        val positionalArgs = args.filter { it !is PyKeywordArgument }
        val posIndex = positionalArgs.indexOf(expr)
        if (posIndex < 0) return null

        val fields = resolved.classAttributes
        if (posIndex >= fields.size) return null
        return fields[posIndex]
    }

    companion object {
        val SUPPORTED_BUILTINS: Set<String> = setOf(
            "int", "str", "float", "bool", "bytes",
            "datetime", "date", "time", "timedelta", "UUID", "Decimal",
            "list", "set", "dict", "tuple", "frozenset"
        )

        val TYPING_ALIASES: Map<String, String> = mapOf(
            "Dict" to "dict",
            "List" to "list",
            "Set" to "set",
            "Tuple" to "tuple",
            "FrozenSet" to "frozenset",
            "typing.Dict" to "dict",
            "typing.List" to "list",
            "typing.Set" to "set",
            "typing.Tuple" to "tuple",
            "typing.FrozenSet" to "frozenset"
        )

        val TYPING_SHORT_NAMES: Set<String> = setOf(
            "Dict", "List", "Set", "Tuple", "FrozenSet"
        )
    }
}
