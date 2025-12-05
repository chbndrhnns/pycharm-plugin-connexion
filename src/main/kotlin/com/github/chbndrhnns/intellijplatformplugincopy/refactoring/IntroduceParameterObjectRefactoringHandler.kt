package com.github.chbndrhnns.intellijplatformplugincopy.refactoring

import com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.PyIntroduceParameterObjectProcessor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

class IntroduceParameterObjectRefactoringHandler : RefactoringActionHandler {
    
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
        
        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }
        
        if (parameters.size < 2) {
            return
        }
        
        PyIntroduceParameterObjectProcessor(function).run()
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Not supported from project view
    }
}
