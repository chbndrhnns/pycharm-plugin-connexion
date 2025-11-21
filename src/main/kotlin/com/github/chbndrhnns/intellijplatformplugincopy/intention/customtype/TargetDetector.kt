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
        if (name !in SUPPORTED_BUILTINS) return null

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
            builtinName = name,
            annotationRef = ref,
            ownerName = identifier,
            dataclassField = dataclassField,
        )
    }

    private fun tryFromExpression(editor: Editor, file: PyFile, leaf: PsiElement): ExpressionTarget? {
        val exprFromCaret = PyTypeIntentions.findExpressionAtCaret(editor, file)
        val expr = exprFromCaret
            ?: PsiTreeUtil.getParentOfType(leaf, PyStringLiteralExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(leaf, PyNumericLiteralExpression::class.java, false)
            ?: return null

        val ctx = TypeEvalContext.codeAnalysis(file.project, file)
        val typeNames = PyTypeIntentions.computeDisplayTypeNames(expr, ctx)
        var candidate: String? = typeNames.expected ?: typeNames.actual

        if (candidate == null || candidate !in SUPPORTED_BUILTINS) {
            candidate = when (expr) {
                is PyStringLiteralExpression -> "str"
                is PyNumericLiteralExpression -> if (expr.isIntegerLiteral) "int" else "float"
                else -> candidate
            }
        }

        candidate ?: return null
        if (candidate !in SUPPORTED_BUILTINS) return null

        if (isAlreadyWrappedInCustomType(expr)) return null

        val preferredNameFromKeyword = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)
            ?.takeIf { it.valueExpression == expr }
            ?.keyword

        val preferredNameFromAssignment = if (preferredNameFromKeyword == null) {
            val assignment = PsiTreeUtil.getParentOfType(expr, PyAssignmentStatement::class.java, false)
            if (assignment != null) {
                val firstTarget = assignment.targets.firstOrNull() as? PyTargetExpression
                firstTarget?.name
            } else null
        } else null

        val dataclassFieldFromExpr = findDataclassFieldForExpression(expr)

        if (dataclassFieldFromExpr != null) {
            val typeDecl =
                PsiTreeUtil.getParentOfType(dataclassFieldFromExpr, PyTypeDeclarationStatement::class.java, false)
            val annExpr = typeDecl?.annotation?.value as? PyExpression
            val annRef = annExpr as? PyReferenceExpression
            val annName = annRef?.name
            if (annName != null && annName !in SUPPORTED_BUILTINS) {
                return null
            }
        }

        return ExpressionTarget(
            builtinName = candidate,
            expression = expr,
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

    private fun isDataclassClass(pyClass: PyClass): Boolean {
        val decorators = pyClass.decoratorList?.decorators
        if (decorators != null && decorators.any { decorator ->
                val name = decorator.name
                val qName = decorator.qualifiedName?.toString()
                name == "dataclass" || qName == "dataclasses.dataclass" || (qName != null && qName.endsWith(".dataclass"))
            }
        ) {
            return true
        }

        val superExprs = pyClass.superClassExpressions
        return superExprs != null && superExprs.any { superExpr ->
            val ref = superExpr as? PyReferenceExpression
            val name = ref?.name
            val qNameOwner = superExpr as? PyQualifiedNameOwner
            val qName = qNameOwner?.qualifiedName
            name == "BaseModel" || (qName != null && qName.endsWith(".BaseModel"))
        }
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
        val SUPPORTED_BUILTINS: Set<String> = setOf("int", "str", "float", "bool", "bytes")
    }
}
