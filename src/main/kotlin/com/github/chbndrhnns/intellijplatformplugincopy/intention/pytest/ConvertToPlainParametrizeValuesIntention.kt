package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

/**
 * Converts pytest.param() calls to plain values in parametrize decorators.
 * Only converts if pytest.param() has no additional arguments (marks, id, etc.)
 */
class ConvertToPlainParametrizeValuesIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Convert pytest.param() to plain values"
    override fun getFamilyName(): String = "Convert pytest.param() to plain values"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableConvertPytestParamIntention) return false
        if (file !is PyFile) return false
        val target = findTarget(editor, file) ?: return false
        return hasConvertiblePytestParamCalls(target.second)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return

        val (_, listArg) = findTarget(editor, file) ?: return

        val generator = PyElementGenerator.getInstance(project)

        // Collect all elements to replace
        val replacements = mutableListOf<Pair<PyExpression, PyExpression>>()

        for (expr in listArg.elements) {
            val callExpr = expr as? PyCallExpression ?: continue
            if (!isPytestParamCall(callExpr)) continue
            if (!canConvertToPlain(callExpr)) continue

            // Extract the first argument
            val firstArg = callExpr.arguments.firstOrNull() ?: continue
            replacements.add(expr to firstArg)
        }

        // Perform replacements
        for ((old, new) in replacements) {
            old.replace(new)
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        run {
            if (file !is PyFile) return@run IntentionPreviewInfo.EMPTY
            val (decorator, listArg) = findTarget(editor, file) ?: return@run IntentionPreviewInfo.EMPTY
            PytestParamPreview.buildConvertToPlain(file, decorator, listArg)
        }

    private fun isParametrizeDecorator(decorator: PyDecorator): Boolean {
        val callee = decorator.callee as? PyQualifiedExpression ?: return false
        val qName = callee.asQualifiedName()?.toString() ?: return false
        return qName == "pytest.mark.parametrize" ||
                qName == "_pytest.mark.parametrize" ||
                qName.endsWith(".pytest.mark.parametrize")
    }

    private fun findParameterValuesListArgument(argumentList: PyArgumentList): PyListLiteralExpression? {
        // parametrize typically has 2 arguments: ("param_names", [values])
        // We want the second argument which should be a list
        val args = argumentList.arguments
        if (args.size < 2) return null

        val secondArg = args[1]
        return secondArg as? PyListLiteralExpression
    }

    private fun hasConvertiblePytestParamCalls(listExpr: PyListLiteralExpression): Boolean {
        return listExpr.elements.any { expr ->
            val callExpr = expr as? PyCallExpression ?: return@any false
            isPytestParamCall(callExpr) && canConvertToPlain(callExpr)
        }
    }

    private fun isPytestParamCall(callExpr: PyCallExpression): Boolean {
        val callee = callExpr.callee as? PyReferenceExpression ?: return false
        val qName = callee.asQualifiedName()?.toString() ?: return false
        return qName == "pytest.param" || qName.endsWith(".pytest.param")
    }

    private fun canConvertToPlain(callExpr: PyCallExpression): Boolean {
        // Can only convert if there's exactly one positional argument and no keyword arguments
        val args = callExpr.arguments
        if (args.isEmpty()) return false

        val hasKeywordArgs = args.any { it is PyKeywordArgument }
        if (hasKeywordArgs) return false

        // Check if there's only one positional argument
        val positionalArgs = args.filterNot { it is PyKeywordArgument }
        return positionalArgs.size == 1
    }

    private fun findTarget(editor: Editor, file: PyFile): Pair<PyDecorator, PyListLiteralExpression>? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null

        // First try to find decorator directly (if caret is on decorator)
        val directDecorator = PsiTreeUtil.getParentOfType(element, PyDecorator::class.java)
        if (directDecorator != null && isParametrizeDecorator(directDecorator)) {
            val argumentList = directDecorator.argumentList ?: return null
            val listArg = findParameterValuesListArgument(argumentList) ?: return null
            return Pair(directDecorator, listArg)
        }

        // Otherwise, find the function and check its decorators
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return null
        val parametrizeDecorator = findParametrizeDecorator(function) ?: return null

        val argumentList = parametrizeDecorator.argumentList ?: return null
        val listArg = findParameterValuesListArgument(argumentList) ?: return null
        return Pair(parametrizeDecorator, listArg)
    }

    private fun findParametrizeDecorator(function: PyFunction): PyDecorator? {
        val decorators = function.decoratorList?.decorators ?: return null
        return decorators.firstOrNull { isParametrizeDecorator(it) }
    }
}
