package com.github.chbndrhnns.intellijplatformplugincopy.intention.dictAccess

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDictGetToTryExceptIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Replace dict.get with try/except KeyError"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        if (!PluginSettingsState.instance().state.enableDictAccessIntention) return false
        if (!PythonVersionGuard.isSatisfiedForElement(element)) return false
        val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: return false
        val callee = call.callee as? PyReferenceExpression ?: return false
        if (callee.referencedName != "get") return false

        val dictExpr = callee.qualifier ?: return false

        // Resolve args (positional or keywords)
        val (_, defaultArg) = getKeyAndDefaultArgs(call) ?: return false
        if (!isSafeDefault(defaultArg)) return false

        val context = TypeEvalContext.codeAnalysis(project, element.containingFile)
        val dictType: PyType? = context.getType(dictExpr)
        if (dictType == null || !PyABCUtil.isSubtype(dictType, PyNames.MAPPING, context)) return false

        // Only when call is the full assigned value (single target) or full return expression
        return when (val parent = call.parent) {
            is PyAssignmentStatement -> {
                if (parent.assignedValue != call) return false
                if (parent.targets.size != 1) return false
                text = PluginConstants.ACTION_PREFIX + "Replace 'dict.get(key, default)' with try/except KeyError"
                true
            }
            is PyReturnStatement -> {
                if (parent.expression != call) return false
                text = PluginConstants.ACTION_PREFIX + "Replace 'dict.get(key, default)' with try/except KeyError"
                true
            }

            else -> false
        }
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: return
        val callee = call.callee as? PyReferenceExpression ?: return
        val dictExpr = callee.qualifier ?: return

        val (keyArg, defaultArg) = getKeyAndDefaultArgs(call) ?: return

        val generator = PyElementGenerator.getInstance(project)
        val level = LanguageLevel.forElement(element)

        val keyText = extractKeyText(keyArg)
        val defaultText = defaultArg.text
        val dictText = dictExpr.text

        val parent = call.parent
        val newText = when (parent) {
            is PyAssignmentStatement -> {
                val targetText = parent.targets.first().text
                // Keep it tight and indentation-safe; IDE will reformat
                """
        try:
            $targetText = $dictText[$keyText]
        except KeyError:
            $targetText = $defaultText
        """.trimIndent()
            }
            is PyReturnStatement -> {
                """
        try:
            return $dictText[$keyText]
        except KeyError:
            return $defaultText
        """.trimIndent()
            }
            else -> return
        }

        val newStmt = generator.createFromText(level, PyStatement::class.java, newText)
        parent.replace(newStmt)
    }

    private fun getKeyAndDefaultArgs(call: PyCallExpression): Pair<PyExpression, PyExpression>? {
        val args = call.arguments
        if (args.isEmpty()) return null

        // Keyword extraction when present
        var keyArg: PyExpression? = null
        var defaultArg: PyExpression? = null

        for (arg in args) {
            when (arg) {
                is PyKeywordArgument -> {
                    when (arg.keyword) {
                        "key" -> keyArg = arg.valueExpression
                        "default" -> defaultArg = arg.valueExpression
                    }
                }
            }
        }

        // Fallback to positional semantics of dict.get
        if (keyArg == null) keyArg = args.firstOrNull { it !is PyKeywordArgument }
        if (defaultArg == null) {
            val positionals = args.filterIsInstance<PyExpression>().filter { it !is PyKeywordArgument }
            if (positionals.size >= 2) defaultArg = positionals[1]
        }

        // Need both arguments present
        val k = keyArg ?: return null
        val d = defaultArg ?: return null
        return k to d
    }

    private fun extractKeyText(keyArg: PyExpression): String {
        // Preserve parentheses for tuple keys written without explicit parens (e.g., a, b)
        return if (keyArg is PyTupleExpression && keyArg.node.findChildByType(PyTokenTypes.LPAR) == null) {
            "(${keyArg.text})"
        } else keyArg.text
    }

    private fun isSafeDefault(expr: PyExpression?): Boolean {
        if (expr == null) return false
        return when (expr) {
            is PyLiteralExpression -> true // numbers, strings, None, True/False
            is PyReferenceExpression -> true // simple name or dotted attr like module.CONST
            is PySubscriptionExpression -> expr.operand is PyReferenceExpression // e.g. CONSTS["x"]
            else -> false
        }
    }
}