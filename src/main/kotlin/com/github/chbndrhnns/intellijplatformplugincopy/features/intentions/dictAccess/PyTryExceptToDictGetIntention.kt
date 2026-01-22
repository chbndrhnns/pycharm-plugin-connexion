package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.dictAccess

import com.github.chbndrhnns.intellijplatformplugincopy.core.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.core.python.PythonVersionGuard
import com.github.chbndrhnns.intellijplatformplugincopy.core.util.isOwnCode
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTryExceptToDictGetIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Replace try-except with dict.get"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        if (!element.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableDictAccessIntention) return false
        if (!PythonVersionGuard.isSatisfiedForElement(element)) return false
        val tryExcept = PsiTreeUtil.getParentOfType(element, PyTryExceptStatement::class.java) ?: return false

        // Trigger: Cursor on `try` keyword or inside the `try` block.
        // We exclude being inside except/else/finally parts.
        val textRange = element.textRange

        for (part in tryExcept.exceptParts) {
            if (part.textRange.contains(textRange)) return false
        }
        if (tryExcept.elsePart?.textRange?.contains(textRange) == true) return false
        if (tryExcept.finallyPart?.textRange?.contains(textRange) == true) return false

        if (extractInfo(tryExcept) != null) {
            text = PluginConstants.ACTION_PREFIX + "Replace try-except with dict.get"
            return true
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val tryExcept = PsiTreeUtil.getParentOfType(element, PyTryExceptStatement::class.java) ?: return
        val info = extractInfo(tryExcept) ?: return

        val generator = PyElementGenerator.getInstance(project)

        // Build arguments for .get()
        // Handle Tuple keys: d[1, 2] -> d.get((1, 2))
        // If key is a tuple without parens, we must add them.
        val keyText = if (info.key is PyTupleExpression && !info.key.text.startsWith("(")) {
            "(${info.key.text})"
        } else {
            info.key.text
        }

        // Handle complex dict expressions: (a + b)[k] -> (a + b).get(k)
        // If dict expression has lower precedence than dot access, wrap in parens.
        val dictText = if (needParentheses(info.dict)) "(${info.dict.text})" else info.dict.text

        val defaultText = info.default.text
        val args = if (defaultText == PyNames.NONE) {
            keyText
        } else {
            "$keyText, $defaultText"
        }

        // Build the new statement text
        val newStatementText = if (info.isReturn) {
            "return $dictText.get($args)"
        } else {
            "${info.target.text} = $dictText.get($args)"
        }

        val newStatement =
            generator.createFromText(LanguageLevel.forElement(element), PyStatement::class.java, newStatementText)

        // Modification
        if (info.isPassCase) {
            // Remove the preceding initialization statement
            val prevStmt = PsiTreeUtil.getPrevSiblingOfType(tryExcept, PyStatement::class.java)
            prevStmt?.delete()
        }

        val replaced = tryExcept.replace(newStatement)
        CodeStyleManager.getInstance(project).reformat(replaced)
    }

    private fun needParentheses(element: PyExpression): Boolean {
        val flat = PyPsiUtils.flattenParens(element)
        return !(flat is PyReferenceExpression || flat is PyCallExpression ||
                flat is PyLiteralExpression || flat is PySubscriptionExpression)
    }

    data class ExtractionInfo(
        val target: PsiElement,
        val dict: PyExpression,
        val key: PyExpression,
        val default: PyExpression,
        val isReturn: Boolean = false,
        val isPassCase: Boolean = false
    )

    private fun extractInfo(tryExcept: PyTryExceptStatement): ExtractionInfo? {
        // Must have exactly one except part
        if (tryExcept.exceptParts.size != 1) return null
        val exceptPart = tryExcept.exceptParts[0]

        // No else or finally
        if (tryExcept.elsePart != null || tryExcept.finallyPart != null) return null

        // Exception class must be KeyError
        val exceptClass = exceptPart.exceptClass as? PyReferenceExpression ?: return null
        val resolvedClass = exceptClass.reference.resolve()
        val keyErrorClass = PyBuiltinCache.getInstance(exceptPart).getClass("KeyError")
        if (resolvedClass == null || resolvedClass != keyErrorClass) return null

        // Try part has exactly one statement
        val tryStmts = tryExcept.tryPart.statementList.statements
        if (tryStmts.size != 1) return null
        val tryStmt = tryStmts[0]

        // Check Try Statement Pattern
        var target: PsiElement? = null
        var dict: PyExpression? = null
        var key: PyExpression? = null
        var isReturn = false

        if (tryStmt is PyAssignmentStatement) {
            // target = dict[key]
            if (tryStmt.targets.size != 1) return null
            val assignmentTarget = tryStmt.targets[0]
            val value = tryStmt.assignedValue

            if (value !is PySubscriptionExpression) return null
            if (!isValidSubscription(value)) return null

            target = assignmentTarget
            dict = value.operand
            key = value.indexExpression
        } else if (tryStmt is PyReturnStatement) {
            // return dict[key]
            val value = tryStmt.expression
            if (value !is PySubscriptionExpression) return null
            if (!isValidSubscription(value)) return null

            isReturn = true
            dict = value.operand
            key = value.indexExpression
            target = tryStmt // Just store the statement itself as placeholder
        } else {
            return null
        }

        if (key == null || target == null) return null

        // Check Mapping type
        val context = TypeEvalContext.codeAnalysis(tryExcept.project, tryExcept.containingFile)
        val dictType = context.getType(dict)
        if (dictType == null || !PyABCUtil.isSubtype(dictType, PyNames.MAPPING, context)) {
            // If type inference fails, we assume it's not a mapping.
            return null
        }

        // Check Except Part
        val exceptStmts = exceptPart.statementList.statements
        if (exceptStmts.size != 1) return null
        val exceptStmt = exceptStmts[0]

        var defaultExpr: PyExpression?
        var isPassCase = false

        if (isReturn) {
            // Expect: return default
            if (exceptStmt !is PyReturnStatement) return null
            defaultExpr = exceptStmt.expression
        } else {
            // Expect: target = default OR pass
            when (exceptStmt) {
                is PyPassStatement -> {
                    // Pass case: check previous sibling of tryExcept
                    val prevStmt = PsiTreeUtil.getPrevSiblingOfType(tryExcept, PyStatement::class.java)
                    if (prevStmt !is PyAssignmentStatement) return null
                    if (prevStmt.targets.size != 1) return null

                    // Must match target
                    if (prevStmt.targets[0].text != target.text) return null

                    defaultExpr = prevStmt.assignedValue
                    isPassCase = true
                }

                is PyAssignmentStatement -> {
                    // Assignment case: target = default
                    if (exceptStmt.targets.size != 1) return null
                    if (exceptStmt.targets[0].text != target.text) return null
                    defaultExpr = exceptStmt.assignedValue
                }

                else -> {
                    return null
                }
            }
        }

        if (defaultExpr == null) return null
        if (!isSafeDefault(defaultExpr)) return null

        return ExtractionInfo(target, dict, key, defaultExpr, isReturn, isPassCase)
    }

    private fun isValidSubscription(sub: PySubscriptionExpression): Boolean {
        // Rule: The subscription operand must be a simple reference or call, not another subscription.
        // Nested subscriptions (d[k1][k2]) change semantics because KeyError in first lookup is not caught by .get(k2)
        val operand = PyPsiUtils.flattenParens(sub.operand)
        return operand !is PySubscriptionExpression
    }

    private fun isSafeDefault(expr: PyExpression): Boolean {
        // Rule: Only support defaults that are literals (strings, numbers, None) or simple references (variables).
        return expr is PyLiteralExpression || expr is PyReferenceExpression
    }
}