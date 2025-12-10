package com.github.chbndrhnns.intellijplatformplugincopy.intention.abstractmethod

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase
import com.jetbrains.python.codeInsight.override.PyMethodMember
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.search.PyClassInheritorsSearch

class ImplementAbstractMethodInChildClassesIntention : IntentionAction, PriorityAction {

    override fun getText(): String = "Implement abstract method in child classes"

    override fun getFamilyName(): String = "Implement abstract method in child classes"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false

        if (!isAbstractMethod(function)) return false

        if (function.containingClass == null) return false

        return true
    }

    private fun isAbstractMethod(function: PyFunction): Boolean {
        val decorators = function.decoratorList?.decorators ?: return false
        return decorators.any {
            val qName = it.qualifiedName?.toString()
            qName == "abc.abstractmethod" || qName == "abstractmethod"
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
        val containingClass = function.containingClass ?: return

        val allInheritors = PyClassInheritorsSearch.search(containingClass, true).findAll()
        val projectScope = GlobalSearchScope.projectScope(project)
        val inheritors = allInheritors.filter {
            val vFile = it.containingFile.virtualFile
            vFile != null && projectScope.contains(vFile)
        }
        val missingImpls = inheritors.filter { it.findMethodByName(function.name, false, null) == null }

        if (missingImpls.isEmpty()) return

        for (targetClass in missingImpls) {
            val member = PyMethodMember(function)

            val targetFile = targetClass.containingFile
            val targetEditor = if (targetFile == file) {
                editor
            } else {
                // Try to find an open editor for the target file
                PsiUtilBase.findEditor(targetClass)
            }

            if (targetEditor != null) {
                PyOverrideImplementUtil.overrideMethods(targetEditor, targetClass, listOf(member), true)
            }
        }
    }

    override fun startInWriteAction(): Boolean = true

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL
}
