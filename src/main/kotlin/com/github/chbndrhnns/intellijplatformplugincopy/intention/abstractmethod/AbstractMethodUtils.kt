package com.github.chbndrhnns.intellijplatformplugincopy.intention.abstractmethod

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilBase
import com.jetbrains.python.codeInsight.override.PyMethodMember
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.search.PyClassInheritorsSearch

object AbstractMethodUtils {
    fun isAbstractMethod(function: PyFunction): Boolean {
        val decorators = function.decoratorList?.decorators ?: return false
        return decorators.any {
            val qName = it.qualifiedName?.toString()
            qName == "abc.abstractmethod" || qName == "abstractmethod"
        }
    }

    fun findInheritorsInScope(baseClass: PyClass, project: Project): List<PyClass> {
        val allInheritors = PyClassInheritorsSearch.search(baseClass, true).findAll()
        val projectScope = GlobalSearchScope.projectScope(project)
        return allInheritors.filter {
            val vFile = it.containingFile.virtualFile
            vFile != null && projectScope.contains(vFile)
        }
    }

    fun implementMethodsInInheritors(
        inheritorToMethods: Map<PyClass, List<PyFunction>>,
        originFile: PsiFile?,
        originEditor: Editor?
    ) {
        for ((targetClass, functions) in inheritorToMethods) {
            val members = functions.map { PyMethodMember(it) }

            val targetFile = targetClass.containingFile
            var targetEditor = if (originFile != null && targetFile == originFile) {
                originEditor
            } else {
                PsiUtilBase.findEditor(targetClass)
            }

            if (targetEditor == null) {
                val vFile = targetFile.virtualFile
                if (vFile != null) {
                    targetEditor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(targetClass.project)
                        .getAllEditors(vFile)
                        .filterIsInstance<com.intellij.openapi.fileEditor.TextEditor>()
                        .firstOrNull()?.editor
                }
            }

            if (targetEditor != null) {
                // Remove pass statements (e.g. empty body) before implementing methods
                targetClass.statementList.statements
                    .filterIsInstance<com.jetbrains.python.psi.PyPassStatement>()
                    .forEach { it.delete() }

                PyOverrideImplementUtil.overrideMethods(targetEditor, targetClass, members, true)
            }
        }
    }
}
