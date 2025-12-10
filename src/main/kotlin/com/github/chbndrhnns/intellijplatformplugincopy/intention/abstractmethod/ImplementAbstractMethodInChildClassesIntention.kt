package com.github.chbndrhnns.intellijplatformplugincopy.intention.abstractmethod

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

class ImplementAbstractMethodInChildClassesIntention : IntentionAction, PriorityAction {

    override fun getText(): String = "Implement abstract method in child classes"

    override fun getFamilyName(): String = "Implement abstract method in child classes"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false

        if (!AbstractMethodUtils.isAbstractMethod(function)) return false

        if (function.containingClass == null) return false

        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
        val containingClass = function.containingClass ?: return

        val inheritors = AbstractMethodUtils.findInheritorsInScope(containingClass, project)
        val missingImpls = inheritors.filter { it.findMethodByName(function.name, false, null) == null }

        if (missingImpls.isEmpty()) return

        val inheritorToMethods = missingImpls.associateWith { listOf(function) }
        AbstractMethodUtils.implementMethodsInInheritors(inheritorToMethods, file, editor)
    }

    override fun startInWriteAction(): Boolean = true

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL
}
