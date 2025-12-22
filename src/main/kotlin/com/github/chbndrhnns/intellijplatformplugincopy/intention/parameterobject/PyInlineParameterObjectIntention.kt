package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class PyInlineParameterObjectIntention : PsiElementBaseIntentionAction() {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Inline parameter object"
    override fun getFamilyName(): String = text

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!PluginSettingsState.instance().state.enableInlineParameterObjectIntention) return false
        return InlineParameterObjectTarget.isAvailable(element)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = InlineParameterObjectTarget.find(element) ?: return
        PyInlineParameterObjectProcessor(function, element).run()
    }
}
