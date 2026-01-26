package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

class CreateFixtureFromParameterIntention : PsiElementBaseIntentionAction(), Iconable {
    override fun getFamilyName(): String = "Create pytest fixture"
    override fun getIcon(flags: Int) = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        if (!PytestFixtureFeatureToggle.isEnabled()) return false
        if (!PluginSettingsState.instance().state.enableCreatePytestFixtureFromParameter) return false
        if (!element.isOwnCode()) return false
        val parameter = findParameterAtOrNear(element) ?: return false
        val fixtureName = parameter.name ?: return false
        if (fixtureName == "self" || fixtureName == "cls") return false
        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return false
        if (!PytestFixtureUtil.isFixtureFunction(function)) return false
        val reference = parameter.references.filterIsInstance<PytestFixtureReference>().firstOrNull() ?: return false
        if (reference.multiResolve(false).isNotEmpty()) return false
        text = PluginConstants.ACTION_PREFIX + "Create pytest fixture '$fixtureName'"
        return true
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        if (!PluginSettingsState.instance().state.enableCreatePytestFixtureFromParameter) {
            return IntentionPreviewInfo.EMPTY
        }
        val element = file.findElementAt(editor.caretModel.offset) ?: return IntentionPreviewInfo.EMPTY
        val parameter = findParameterAtOrNear(element) ?: return IntentionPreviewInfo.EMPTY
        val fixtureName = parameter.name ?: return IntentionPreviewInfo.EMPTY
        createFixtureFromParameter(project, parameter, fixtureName, isPreview = true)
        return IntentionPreviewInfo.DIFF
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        if (!PluginSettingsState.instance().state.enableCreatePytestFixtureFromParameter) return
        val parameter = findParameterAtOrNear(element) ?: return
        val fixtureName = parameter.name ?: return
        createFixtureFromParameter(project, parameter, fixtureName, isPreview = false)
    }

    private fun findParameterAtOrNear(element: PsiElement): PyNamedParameter? {
        if (element is PyNamedParameter) return element
        PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java)?.let { return it }
        val prevLeaf = PsiTreeUtil.prevLeaf(element, true)
        if (prevLeaf != null) {
            PsiTreeUtil.getParentOfType(prevLeaf, PyNamedParameter::class.java)?.let { return it }
        }
        val nextLeaf = PsiTreeUtil.nextLeaf(element, true)
        if (nextLeaf != null) {
            PsiTreeUtil.getParentOfType(nextLeaf, PyNamedParameter::class.java)?.let { return it }
        }
        return null
    }
}
