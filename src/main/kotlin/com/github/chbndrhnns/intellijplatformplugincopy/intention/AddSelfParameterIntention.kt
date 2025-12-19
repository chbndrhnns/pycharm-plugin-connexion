package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFunction

class AddSelfParameterIntention : PsiElementBaseIntentionAction(), PriorityAction {
    override fun getFamilyName() = "Add 'self' parameter"
    override fun getText() = PluginConstants.ACTION_PREFIX + "Add 'self' parameter"

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = element.parentOfType<PyFunction>() ?: return false
        if (function.containingClass == null) return false

        val decorators = function.decoratorList?.decorators ?: emptyArray()
        if (decorators.any { it.name == "staticmethod" }) return false

        val parameters = function.parameterList.parameters
        if (parameters.isEmpty()) return true

        val firstParam = parameters[0]
        return firstParam.name != "self"
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = element.parentOfType<PyFunction>() ?: return

        val params = function.parameterList

        val generator = PyElementGenerator.getInstance(project)
        val selfParam = generator.createParameter("self")

        val firstParam = params.parameters.firstOrNull()
        if (firstParam != null) {
            params.addBefore(selfParam, firstParam)
            val comma = generator.createComma().psi
            params.addBefore(comma, firstParam)
        } else {
            params.addParameter(selfParam)
        }
    }
}
