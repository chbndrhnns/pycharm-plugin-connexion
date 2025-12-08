package com.github.chbndrhnns.intellijplatformplugincopy.intention.dictAccess

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDictGetToTryExceptIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Replace dict.get with try/except KeyError"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: return false
        val callee = call.callee as? PyReferenceExpression ?: return false

        if (callee.name != "get") return false
        val qualifier = callee.qualifier ?: return false

        // Need at least key and default arguments
        val args = call.arguments
        if (args.size < 2) return false

        val keyArg = args[0]
        val defaultArg = args[1]
        if (!isSafeDefault(defaultArg)) return false

        val context = TypeEvalContext.codeAnalysis(project, element.containingFile)
        val type = context.getType(qualifier)
        if (type == null || !PyABCUtil.isSubtype(type, com.jetbrains.python.PyNames.MAPPING, context)) return false

        val parent = call.parent
        when (parent) {
            is PyAssignmentStatement -> {
                if (parent.assignedValue != call) return false
                if (parent.targets.size != 1) return false
                text = "Replace 'dict.get(key, default)' with try/except KeyError"
                return true
            }

            is PyReturnStatement -> {
                if (parent.expression != call) return false
                text = "Replace 'dict.get(key, default)' with try/except KeyError"
                return true
            }

            else -> return false
        }
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: return
        val callee = call.callee as? PyReferenceExpression ?: return
        val qualifier = callee.qualifier ?: return
        val args = call.arguments
        if (args.size < 2) return

        val keyArg = args[0]
        val defaultArg = args[1]

        val parent = call.parent
        val generator = PyElementGenerator.getInstance(project)

        val keyText = extractKeyText(keyArg)
        val defaultText = defaultArg.text
        val dictText = qualifier.text

        val newStatementText = when (parent) {
            is PyAssignmentStatement -> {
                val targetText = parent.targets.first().text
                listOf(
                    "try:",
                    "    $targetText = $dictText[$keyText]",
                    "except KeyError:",
                    "    $targetText = $defaultText"
                ).joinToString("\n")
            }

            is PyReturnStatement -> {
                listOf(
                    "try:",
                    "    return $dictText[$keyText]",
                    "except KeyError:",
                    "    return $defaultText"
                ).joinToString("\n")
            }

            else -> return
        }

        val newStatement =
            generator.createFromText(LanguageLevel.forElement(element), PyStatement::class.java, newStatementText)
        parent.replace(newStatement)
    }

    private fun extractKeyText(keyArg: PyExpression): String {
        if (keyArg is PyTupleExpression && keyArg.node.findChildByType(PyTokenTypes.LPAR) == null) {
            return "(${keyArg.text})"
        }
        return keyArg.text
    }

    private fun isSafeDefault(expr: PyExpression): Boolean {
        return expr is PyLiteralExpression || expr is PyReferenceExpression
    }
}