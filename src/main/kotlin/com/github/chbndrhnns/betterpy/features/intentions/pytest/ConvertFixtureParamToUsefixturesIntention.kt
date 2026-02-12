package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.features.pytest.fixture.PytestFixtureFeatureToggle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class ConvertFixtureParamToUsefixturesIntention : IntentionAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Convert to @pytest.mark.usefixtures()"
    override fun getFamilyName(): String = "Convert fixture param to usefixtures"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PytestFixtureFeatureToggle.isEnabled()) return false
        if (file !is PyFile) return false

        val param = findTargetParameter(file, editor) ?: return false
        val name = param.name ?: return false
        if (name == "self" || name == "cls" || name == "request") return false

        val function = PsiTreeUtil.getParentOfType(param, PyFunction::class.java) ?: return false
        return PytestNaming.isTestFunction(function)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        val param = findTargetParameter(file, editor) ?: return
        val fixtureName = param.name ?: return
        val function = PsiTreeUtil.getParentOfType(param, PyFunction::class.java) ?: return
        val generator = PyElementGenerator.getInstance(project)

        // Remove the parameter
        param.delete()
        cleanupParameterList(function.parameterList)

        // Add to existing @pytest.mark.usefixtures or create new
        val existingUsefixtures = findUsefixturesDecorator(function)
        if (existingUsefixtures != null) {
            val argumentList = existingUsefixtures.argumentList ?: return
            val newArg = generator.createStringLiteralAlreadyEscaped("\"$fixtureName\"")
            argumentList.addArgument(newArg)
        } else {
            PyUtil.addDecorator(function, "@pytest.mark.usefixtures(\"$fixtureName\")")
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF

    private fun findTargetParameter(file: PsiFile, editor: Editor): PyNamedParameter? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java, false)
    }

    private fun cleanupParameterList(parameterList: PyParameterList) {
        for (child in parameterList.children) {
            if (child.text.trim() == ",") {
                child.delete()
            }
        }
    }

    private fun findUsefixturesDecorator(function: PyFunction): PyDecorator? {
        val decorators = function.decoratorList?.decorators ?: return null
        return decorators.firstOrNull { decorator ->
            val qualifiedName = decorator.callee?.let { (it as? PyQualifiedExpression)?.asQualifiedName()?.toString() }
            qualifiedName == "pytest.mark.usefixtures"
        }
    }
}
