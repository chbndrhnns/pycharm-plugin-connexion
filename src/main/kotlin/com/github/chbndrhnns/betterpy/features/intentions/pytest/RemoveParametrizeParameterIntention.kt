package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.pytest.PytestParametrizeUtil
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class RemoveParametrizeParameterIntention : IntentionAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Remove parametrize parameter"
    override fun getFamilyName(): String = "Remove parametrize parameter"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableParametrizePytestTestIntention) return false
        if (file !is PyFile) return false

        val context = findContext(file, editor) ?: return false
        val decorator = context.decorator ?: return false
        val argList = decorator.argumentList ?: return false
        val args = argList.arguments
        if (args.isEmpty()) return false

        val namesArg = ParametrizeSignatureUtil.findNamesArg(args, decorator) ?: return false
        val names = PytestParametrizeUtil.extractParameterNames(namesArg) ?: return false
        // Need at least 2 params to remove one
        if (names.size < 2) return false

        // Check caret is on a specific parameter name
        if (context.caretParamIndex < 0) return false

        val function = context.function ?: return false

        // Reject removal if the parameter is used in the function body
        val paramName = names[context.caretParamIndex]
        val param = function.parameterList.findParameterByName(paramName) ?: return false
        val body = function.statementList
        val usages = ReferencesSearch.search(param, LocalSearchScope(body)).findFirst()
        return usages == null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        val context = findContext(file, editor) ?: return
        val decorator = context.decorator ?: return
        val function = context.function ?: return
        val argList = decorator.argumentList ?: return
        val args = argList.arguments
        if (args.isEmpty()) return

        val namesArg = ParametrizeSignatureUtil.findNamesArg(args, decorator) ?: return
        val existingNames = PytestParametrizeUtil.extractParameterNames(namesArg) ?: return
        val removeIndex = context.caretParamIndex
        if (removeIndex < 0 || removeIndex >= existingNames.size) return

        val removedName = existingNames[removeIndex]
        val generator = PyElementGenerator.getInstance(project)

        // 1. Update argnames
        ParametrizeSignatureUtil.removeNameFromArgnames(namesArg, removeIndex, existingNames, generator)

        // 2. Update value tuples
        val valuesArg = ParametrizeSignatureUtil.findValuesArg(args, decorator)
        if (valuesArg is PyListLiteralExpression) {
            ParametrizeSignatureUtil.removeValueFromEachEntry(valuesArg, removeIndex, existingNames.size, generator)
        }

        // 3. Remove function parameter
        ParametrizeSignatureUtil.removeFunctionParameter(function, removedName)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF

    private fun findContext(file: PsiFile, editor: Editor): RemoveParametrizeContext? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val decorator = PsiTreeUtil.getParentOfType(element, PyDecorator::class.java) ?: return null
        if (!PytestParametrizeUtil.isParametrizeDecorator(decorator, allowBareName = true)) return null
        val function = PsiTreeUtil.getParentOfType(decorator, PyFunction::class.java) ?: return null

        val argList = decorator.argumentList ?: return null
        val args = argList.arguments
        if (args.isEmpty()) return null
        val namesArg = ParametrizeSignatureUtil.findNamesArg(args, decorator) ?: return null
        val names = PytestParametrizeUtil.extractParameterNames(namesArg) ?: return null

        val caretIndex =
            ParametrizeSignatureUtil.findCaretParamIndex(element, namesArg, names, editor.caretModel.offset)
        return RemoveParametrizeContext(decorator, function, caretIndex)
    }
}

private data class RemoveParametrizeContext(
    val decorator: PyDecorator?,
    val function: PyFunction?,
    val caretParamIndex: Int
)
