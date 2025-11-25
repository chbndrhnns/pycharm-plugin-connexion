package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.CustomTypeConstants.SUPPORTED_BUILTINS
import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.CustomTypeConstants.TYPING_ALIASES
import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.CustomTypeConstants.TYPING_SHORT_NAMES
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Extracted logic for locating an intention target at the caret.
 */
class TargetDetector {

    fun find(editor: Editor, file: PyFile): Target? {
        val offset = editor.caretModel.offset
        val leaf = file.findElementAt(offset) ?: return null

        tryFromAnnotation(leaf)?.let { return it }
        tryFromVariableDefinition(leaf)?.let { return it }
        tryFromFStringReference(leaf)?.let { return it }
        return tryFromExpression(editor, file, leaf)
    }

    private fun tryFromVariableDefinition(leaf: PsiElement): AnnotationTarget? {
        val target = PsiTreeUtil.getParentOfType(leaf, PyTargetExpression::class.java, false) ?: return null
        return createTargetFromDefinition(target)
    }

    private fun tryFromFStringReference(leaf: PsiElement): AnnotationTarget? {
        val ref = PsiTreeUtil.getParentOfType(leaf, PyReferenceExpression::class.java, false) ?: return null

        // Verify we are inside a string literal (implies f-string interpolation for references)
        PsiTreeUtil.getParentOfType(ref, PyStringLiteralExpression::class.java) ?: return null

        val resolved = ref.reference.resolve()

        if (resolved is PyTargetExpression) {
            return createTargetFromDefinition(resolved)
        }
        if (resolved is PyNamedParameter) {
            return createTargetFromParameter(resolved)
        }

        return null
    }

    private fun createTargetFromParameter(target: PyNamedParameter): AnnotationTarget? {
        val annotation = target.annotation ?: return null
        val annotationExpr = annotation.value as? PyReferenceExpression ?: return null

        val name = annotationExpr.name ?: return null
        val normalizedName = normalizeName(name, annotationExpr) ?: return null

        return AnnotationTarget(
            builtinName = normalizedName,
            annotationRef = annotationExpr,
            ownerName = target.name,
            dataclassField = null,
        )
    }

    private fun createTargetFromDefinition(target: PyTargetExpression): AnnotationTarget? {
        val annotation = target.annotation ?: return null
        val annotationExpr = annotation.value as? PyReferenceExpression ?: return null

        val name = annotationExpr.name ?: return null
        val normalizedName = normalizeName(name, annotationExpr) ?: return null

        val dataclassField = target.takeIf { isDataclassField(it) }

        return AnnotationTarget(
            builtinName = normalizedName,
            annotationRef = annotationExpr,
            ownerName = target.name,
            dataclassField = dataclassField,
        )
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

        if (isArgumentOfLibraryFunction(expr)) return null
        val ctx = TypeEvalContext.codeAnalysis(file.project, file)

        // 1. Determine the builtin type name (candidate)
        val builtinName = determineBuiltinType(expr, ctx) ?: return null

        // 2. Check if already wrapped
        if (isAlreadyWrappedInCustomType(expr)) return null

        // 3. Detect metadata (assignment, keyword arg, dataclass field, parameter default)
        val keywordName = detectKeywordArgumentName(expr)
        val assignmentInfo = if (keywordName == null) detectAssignmentName(expr, builtinName) else null
        val parameterInfo =
            if (keywordName == null && assignmentInfo == null) detectParameterDefaultInfo(expr, builtinName) else null
        val dataclassField = findDataclassFieldForExpression(expr)

        // Check dataclass field type conflict
        if (dataclassField != null) {
            if (hasConflictingDataclassType(dataclassField)) return null
        }

        return ExpressionTarget(
            builtinName = builtinName,
            expression = expr,
            annotationRef = assignmentInfo?.second ?: parameterInfo?.second,
            keywordName = keywordName,
            assignmentName = assignmentInfo?.first,
            parameterName = parameterInfo?.first,
            dataclassField = dataclassField,
        )
    }

    private fun determineBuiltinType(expr: PyExpression, ctx: TypeEvalContext): String? {
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

        val candidate: String? = typeNames.expected ?: typeNames.actual
        var normalizedCandidate = candidate?.let { normalizeName(it) }

        if (normalizedCandidate == null) {
            normalizedCandidate = detectFromLiteral(expr)
        }

        normalizedCandidate ?: return null

        // Final check
        if (normalizedCandidate !in SUPPORTED_BUILTINS) return null

        return normalizedCandidate
    }

    private fun detectFromLiteral(expr: PyExpression): String? {
        return when (expr) {
            is PyStringLiteralExpression -> "str"
            is PyNumericLiteralExpression -> if (expr.isIntegerLiteral) "int" else "float"
            else -> null
        }
    }

    private fun detectParameterDefaultInfo(
        expr: PyExpression,
        builtinName: String
    ): Pair<String, PyReferenceExpression?>? {
        val param = PsiTreeUtil.getParentOfType(expr, PyNamedParameter::class.java, false)
        if (param != null && param.defaultValue == expr) {
            val annotationValue = param.annotation?.value
            val ref = findRefMatchingBuiltin(annotationValue, builtinName)
            return Pair(param.name ?: "", ref)
        }
        return null
    }

    private fun findRefMatchingBuiltin(annotationValue: PyExpression?, builtinName: String): PyReferenceExpression? {
        if (annotationValue == null) return null

        val refs = mutableListOf<PyReferenceExpression>()
        if (annotationValue is PyReferenceExpression) {
            refs.add(annotationValue)
        }
        refs.addAll(PsiTreeUtil.findChildrenOfType(annotationValue, PyReferenceExpression::class.java))

        return refs.firstOrNull { ref ->
            val normalized = normalizeName(ref.name ?: "", ref)
            normalized == builtinName
        }
    }

    private fun detectKeywordArgumentName(expr: PyExpression): String? {
        return PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)
            ?.takeIf { it.valueExpression == expr }
            ?.keyword
    }

    private fun detectAssignmentName(expr: PyExpression, builtinName: String): Pair<String?, PyReferenceExpression?>? {
        val assignment = PsiTreeUtil.getParentOfType(expr, PyAssignmentStatement::class.java, false) ?: return null

        val firstTarget = assignment.targets.firstOrNull() as? PyTargetExpression
        val annotation = firstTarget?.annotation
        val annotationValue = annotation?.value

        val annotationRef = findRefMatchingBuiltin(annotationValue, builtinName)

        return Pair(firstTarget?.name, annotationRef)
    }

    private fun hasConflictingDataclassType(dataclassField: PyTargetExpression): Boolean {
        val typeDecl =
            PsiTreeUtil.getParentOfType(dataclassField, PyTypeDeclarationStatement::class.java, false)
        val annExpr = typeDecl?.annotation?.value
        val annRef = annExpr as? PyReferenceExpression
        val annName = annRef?.name
        val normalizedAnn = annName?.let { normalizeName(it, annRef) }

        return normalizedAnn != null && normalizedAnn !in SUPPORTED_BUILTINS
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


    private fun isArgumentOfLibraryFunction(expr: PyExpression): Boolean {
        val parent = expr.parent
        val argList = when {
            parent is PyArgumentList -> parent
            parent is PyKeywordArgument && parent.parent is PyArgumentList -> parent.parent as PyArgumentList
            else -> return false
        }

        val call = argList.parent as? PyCallExpression ?: return false
        val callee = call.callee as? PyReferenceExpression ?: return false
        val resolved = callee.reference.resolve() ?: return false
        val vFile = resolved.containingFile?.virtualFile ?: return false
        return !ProjectFileIndex.getInstance(expr.project).isInContent(vFile)
    }
}
