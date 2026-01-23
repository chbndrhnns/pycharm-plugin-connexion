package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.core.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.core.pytest.PytestParametrizeUtil
import com.github.chbndrhnns.intellijplatformplugincopy.core.util.isOwnCode
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
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
        if (!file.isOwnCode()) return false
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
        return PytestParametrizeUtil.isParametrizeDecorator(decorator, allowBareName = false)
    }

    private fun findParameterValuesListArgument(argumentList: PyArgumentList): PyListLiteralExpression? {
        return PytestParametrizeUtil.findParameterValuesListArgument(argumentList)
    }

    private fun hasConvertiblePytestParamCalls(listExpr: PyListLiteralExpression): Boolean {
        return listExpr.elements.any { expr ->
            val callExpr = expr as? PyCallExpression ?: return@any false
            PytestParametrizeUtil.isPytestParamCall(callExpr) && PytestParametrizeUtil.canConvertToPlain(callExpr)
        }
    }

    private fun isPytestParamCall(callExpr: PyCallExpression): Boolean {
        return PytestParametrizeUtil.isPytestParamCall(callExpr)
    }

    private fun canConvertToPlain(callExpr: PyCallExpression): Boolean {
        return PytestParametrizeUtil.canConvertToPlain(callExpr)
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
        return decorators.firstOrNull { PytestParametrizeUtil.isParametrizeDecorator(it, allowBareName = false) }
    }
}
