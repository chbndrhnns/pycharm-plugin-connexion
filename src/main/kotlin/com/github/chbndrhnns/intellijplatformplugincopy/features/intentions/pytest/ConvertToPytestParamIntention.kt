package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.core.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.core.util.isOwnCode
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.PytestParametrizeUtil
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

/**
 * Converts plain values to pytest.param() calls in parametrize decorators.
 */
class ConvertToPytestParamIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Convert argument to pytest.param()"
    override fun getFamilyName(): String = "Convert argument to pytest.param()"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableConvertPytestParamIntention) return false
        if (file !is PyFile) return false
        val target = findTarget(editor, file) ?: return false
        return hasPlainValues(target.second)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return

        val (_, listArg) = findTarget(editor, file) ?: return

        PytestParametrizeUtil.ensurePytestImported(file)

        val generator = PyElementGenerator.getInstance(project)

        // Collect all elements to replace
        val replacements = mutableListOf<Pair<PyExpression, PyExpression>>()

        for (expr in listArg.elements) {
            // Skip if already a pytest.param() call
            if (expr is PyCallExpression && PytestParametrizeUtil.isPytestParamCall(expr)) continue

            // Wrap the value in pytest.param()
            val wrappedExpr = generator.createExpressionFromText(
                LanguageLevel.getDefault(),
                "pytest.param(${expr.text})"
            )
            replacements.add(expr to wrappedExpr)
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
            PytestParamPreview.buildConvertToPytestParam(file, decorator, listArg)
        }

    private fun isParametrizeDecorator(decorator: PyDecorator): Boolean {
        return PytestParametrizeUtil.isParametrizeDecorator(decorator, allowBareName = false)
    }

    private fun findParameterValuesListArgument(argumentList: PyArgumentList): PyListLiteralExpression? {
        return PytestParametrizeUtil.findParameterValuesListArgument(argumentList)
    }

    private fun hasPlainValues(listExpr: PyListLiteralExpression): Boolean {
        return listExpr.elements.any { expr ->
            // It's a plain value if it's not a pytest.param() call
            !(expr is PyCallExpression && PytestParametrizeUtil.isPytestParamCall(expr))
        }
    }

    private fun isPytestParamCall(callExpr: PyCallExpression): Boolean {
        return PytestParametrizeUtil.isPytestParamCall(callExpr)
    }

    private fun findTarget(editor: Editor, file: PyFile): Pair<PyDecorator, PyListLiteralExpression>? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null

        // Only available when the caret is inside the @pytest.mark.parametrize decorator.
        // (Being somewhere inside the decorated function should not make this intention available.)
        val decorator = PsiTreeUtil.getParentOfType(element, PyDecorator::class.java) ?: return null
        if (!isParametrizeDecorator(decorator)) return null

        val argumentList = decorator.argumentList ?: return null
        val listArg = findParameterValuesListArgument(argumentList) ?: return null
        return Pair(decorator, listArg)
    }

    internal companion object {
        fun isPytestParamCallStatic(callExpr: PyCallExpression): Boolean {
            return PytestParametrizeUtil.isPytestParamCall(callExpr)
        }
    }

}
