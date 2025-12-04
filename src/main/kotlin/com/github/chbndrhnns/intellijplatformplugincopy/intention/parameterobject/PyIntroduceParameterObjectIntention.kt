package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

class PyIntroduceParameterObjectIntention : PsiElementBaseIntentionAction() {

    override fun getText(): String = "Introduce parameter object"
    override fun getFamilyName(): String = text

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false
        
        if (function.containingFile.name.endsWith(".pyi")) return false

        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }

        if (parameters.size < 2) return false
        
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
        PyIntroduceParameterObjectProcessor(function).run()
    }
}
