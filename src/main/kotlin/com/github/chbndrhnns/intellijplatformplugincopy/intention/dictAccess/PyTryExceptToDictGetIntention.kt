package com.github.chbndrhnns.intellijplatformplugincopy.intention.dictAccess

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTryExceptToDictGetIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Replace try-except with dict.get"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
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
            text = "Replace try-except with dict.get"
            return true
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val tryExcept = PsiTreeUtil.getParentOfType(element, PyTryExceptStatement::class.java) ?: return
        val info = extractInfo(tryExcept) ?: return

        val generator = PyElementGenerator.getInstance(project)

        // Build arguments for .get()
        // If default is "None" literally, we can omit it.
        val defaultText = info.default.text
        val args = if (defaultText == "None") {
            info.key.text
        } else {
            "${info.key.text}, $defaultText"
        }

        // Build the new statement text
        val newStatementText = if (info.isReturn) {
            "return ${info.dict.text}.get($args)"
        } else {
            "${info.target.text} = ${info.dict.text}.get($args)"
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

    data class ExtractionInfo(
        val target: PsiElement, // The variable or return keyword logic is handled by isReturn flag
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
        val exceptClass = exceptPart.exceptClass ?: return null
        if (exceptClass.text != "KeyError") return null
        if (exceptClass !is PyReferenceExpression) return null

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

        var defaultExpr: PyExpression? = null
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
        val operand = sub.operand
        return operand !is PySubscriptionExpression
    }

    private fun isSafeDefault(expr: PyExpression): Boolean {
        // Rule: Only support defaults that are literals (strings, numbers, None) or simple references (variables).
        if (expr is PyNumericLiteralExpression) return true
        if (expr is PyLiteralExpression) return true
        if (expr is PyReferenceExpression) return true
        return false
    }
}
