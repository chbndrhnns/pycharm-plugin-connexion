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
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class AddParametrizeParameterIntention : IntentionAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Add parametrize parameter"
    override fun getFamilyName(): String = "Add parametrize parameter"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableParametrizePytestTestIntention) return false
        if (file !is PyFile) return false

        val context = findContext(file, editor) ?: return false
        return context.decorator != null
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
        val newParamName = ParametrizeSignatureUtil.generateNewParamName(existingNames)

        val generator = PyElementGenerator.getInstance(project)

        // 1. Update argnames
        ParametrizeSignatureUtil.addNameToArgnames(namesArg, newParamName, generator)

        // 2. Update value tuples
        val valuesArg = ParametrizeSignatureUtil.findValuesArg(args, decorator)
        if (valuesArg is PyListLiteralExpression) {
            ParametrizeSignatureUtil.addValueToEachEntry(valuesArg, existingNames.size, generator)
        }

        // 3. Add function parameter
        ParametrizeSignatureUtil.addFunctionParameter(function, newParamName, generator)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF

    private fun findContext(file: PsiFile, editor: Editor): ParametrizeContext? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val decorator = PsiTreeUtil.getParentOfType(element, PyDecorator::class.java) ?: return null
        if (!PytestParametrizeUtil.isParametrizeDecorator(decorator, allowBareName = true)) return null
        val function = PsiTreeUtil.getParentOfType(decorator, PyFunction::class.java) ?: return null
        return ParametrizeContext(decorator, function)
    }
}

private data class ParametrizeContext(val decorator: PyDecorator?, val function: PyFunction?)
